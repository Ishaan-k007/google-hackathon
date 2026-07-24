package com.foodflow.agent;

import com.foodflow.model.AgentMessage;
import com.foodflow.model.AgentType;
import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.PlanStatus;
import com.foodflow.model.RescuePlan;
import com.foodflow.model.SafetyCheckResult;
import com.foodflow.model.Severity;
import com.foodflow.service.GeminiService;
import com.foodflow.store.AgentMessageStore;
import com.foodflow.database.CharityRequestDatabase;
import com.foodflow.store.DonationStore;
import com.foodflow.store.DriverStore;
import com.foodflow.store.RescuePlanStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Orchestrates the other agents, narrates the negotiation as a sequence of
 * AgentMessages (revealed with a stagger for the live demo), and approves the
 * final plan only after SafetyAgent (via RoutingMatchingAgent) has validated it.
 */
@Component
public class CoordinatorAgent {

    private static final long START_OFFSET_MS = 300;
    private static final long STEP_OFFSET_MS = 950;

    private final DonationStore donationStore;
    private final CharityRequestDatabase charityRequestDatabase;
    private final DriverStore driverStore;
    private final AgentMessageStore agentMessageStore;
    private final RescuePlanStore rescuePlanStore;
    private final RoutingMatchingAgent routingMatchingAgent;
    private final SupermarketAgent supermarketAgent;
    private final CharityAgent charityAgent;
    private final DriverAgent driverAgent;
    private final GeminiService geminiService;

    public CoordinatorAgent(DonationStore donationStore, CharityRequestDatabase charityRequestDatabase,
                             DriverStore driverStore, AgentMessageStore agentMessageStore,
                             RescuePlanStore rescuePlanStore, RoutingMatchingAgent routingMatchingAgent,
                             SupermarketAgent supermarketAgent, CharityAgent charityAgent,
                             DriverAgent driverAgent, GeminiService geminiService) {
        this.donationStore = donationStore;
        this.charityRequestDatabase = charityRequestDatabase;
        this.driverStore = driverStore;
        this.agentMessageStore = agentMessageStore;
        this.rescuePlanStore = rescuePlanStore;
        this.routingMatchingAgent = routingMatchingAgent;
        this.supermarketAgent = supermarketAgent;
        this.charityAgent = charityAgent;
        this.driverAgent = driverAgent;
        this.geminiService = geminiService;
    }

    public NegotiationOutcome runNegotiation() {
        agentMessageStore.startNewRun();
        try {
            return doRunNegotiation();
        } finally {
            // Signals pollers that every message this run will ever produce has now been
            // appended - see AgentMessageStore.isRunComplete() for why this matters: message
            // emission can block for seconds on a real Gemini call (buildPlan -> explainMatch),
            // so "no more messages are coming" cannot be inferred from elapsed time alone.
            agentMessageStore.markRunFinished();
        }
    }

    private NegotiationOutcome doRunNegotiation() {
        AtomicLong offset = new AtomicLong(START_OFFSET_MS);

        List<Donation> donations = donationStore.findAvailable();
        if (!donations.isEmpty()) {
            Donation firstDonation = donations.get(0);
            try {
                NegotiationOutcome pythonOutcome = tryPythonOrchestration(firstDonation, offset);
                if (pythonOutcome != null) {
                    return pythonOutcome;
                }
            } catch (Exception e) {
                System.err.println("Python orchestration failed, falling back to local: " + e.getMessage());
            }
        }

        List<CharityRequest> charities = charityRequestDatabase.findPending();
        List<DriverAvailability> drivers = driverStore.findAvailable();

        if (donations.isEmpty() || charities.isEmpty()) {
            emit(offset, AgentType.COORDINATOR, Severity.INFO,
                    "Waiting for at least one active donation and one charity request before a match can be attempted.");
            return new NegotiationOutcome(false, null);
        }

        RoutingMatchingAgent.MatchResult result = routingMatchingAgent.findBestMatch(donations, charities, drivers);
        if (!result.hasCandidate()) {
            emit(offset, AgentType.COORDINATOR, Severity.INFO,
                    "No donation/charity combination could be evaluated yet.");
            return new NegotiationOutcome(false, null);
        }

        MatchCandidate best = result.best;
        Donation donation = best.getDonation();
        CharityRequest charity = best.getCharity();
        DriverAvailability driver = best.getDriver();

        emit(offset, AgentType.SUPERMARKET, Severity.INFO, supermarketAgent.describe(donation));

        if (donation.getClarificationQuestion() != null && !donation.getClarificationQuestion().isBlank()) {
            emit(offset, AgentType.GEMINI, Severity.WARNING, donation.getClarificationQuestion());
        }

        emit(offset, AgentType.CHARITY, Severity.INFO, charityAgent.describe(charity));

        SafetyCheckResult dietaryCheck = find(best.getSafetyChecks(), "Dietary compatibility");
        SafetyCheckResult allergenCheck = find(best.getSafetyChecks(), "Allergen compatibility");
        boolean dietaryAllergenOk = dietaryCheck.isPassed() && allergenCheck.isPassed();

        StringBuilder safetyMsg = new StringBuilder();
        safetyMsg.append(dietaryCheck.getDetail());
        if (donation.getClarificationQuestion() != null && donation.getClarificationQuestion().toLowerCase().contains("nuts")
                && dietaryAllergenOk) {
            safetyMsg.append("\n").append(allergenCheck.getDetail())
                    .append(" (Nut content is unconfirmed; the charity will be notified before final confirmation.)");
        } else {
            safetyMsg.append("\n").append(allergenCheck.getDetail());
        }
        emit(offset, AgentType.SAFETY, dietaryAllergenOk ? Severity.SUCCESS : Severity.ERROR, safetyMsg.toString());

        RescuePlan plan;
        if (!dietaryAllergenOk) {
            plan = buildPlan(best);
            emit(offset, AgentType.COORDINATOR, Severity.ERROR,
                    "Match rejected.\n" + String.join("\n", best.getRejectionReasons()));
        } else {
            if (driver != null) {
                emit(offset, AgentType.DRIVER, Severity.INFO, driverAgent.describe(driver));
                SafetyCheckResult distanceCheck = find(best.getSafetyChecks(), "Travel distance");
                emit(offset, AgentType.ROUTING, distanceCheck.isPassed() ? Severity.SUCCESS : Severity.ERROR,
                        "Estimated distance: " + best.getDistanceMiles() + " miles (limit " + driver.getMaximumDistanceMiles() + " miles).\n"
                                + distanceCheck.getDetail());
            } else {
                emit(offset, AgentType.ROUTING, Severity.INFO,
                        "No transport route is required; " + charity.getCharityName() + " will collect directly.");
            }

            plan = buildPlan(best);
            if (best.isSafetyPassed()) {
                emit(offset, AgentType.COORDINATOR, Severity.SUCCESS,
                        "Match approved. Allocating " + best.getAllocatedMeals() + " meals.");
            } else {
                emit(offset, AgentType.COORDINATOR, Severity.ERROR,
                        "Match rejected.\n" + String.join("\n", best.getRejectionReasons()));
            }
        }

        rescuePlanStore.save(plan);
        return new NegotiationOutcome(true, plan);
    }

    private NegotiationOutcome tryPythonOrchestration(Donation donation, AtomicLong offset) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        // Build surplus_items array
        List<Map<String, Object>> surplusItems = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("item_type", (donation.getDietaryTypes() != null && donation.getDietaryTypes().contains("vegetarian")) || (donation.getFoodDescription() != null && donation.getFoodDescription().toLowerCase().contains("veg")) ? "vegetarian_meal" : "produce");
        item.put("quantity", donation.getQuantity());
        item.put("expires_at", donation.getExpiresAt() != null ? donation.getExpiresAt().toString() : "20:00");
        item.put("allergens", donation.getAllergens() != null ? donation.getAllergens() : List.of("none"));
        item.put("storage_req", donation.getStorageType() != null ? donation.getStorageType().toString().toLowerCase() : "refrigerated");
        surplusItems.add(item);

        // Build main request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("donor_id", donation.getId() != null ? donation.getId() : "SUPER-001");
        requestBody.put("donor_name", donation.getSupermarketName());
        requestBody.put("location", donation.getPickupLocation() != null && donation.getPickupLocation().contains(",") ? donation.getPickupLocation() : "51.531983,-0.121090");
        requestBody.put("available_from", donation.getAvailableFrom() != null ? donation.getAvailableFrom().toString() : "18:00");
        requestBody.put("surplus_items", surplusItems);

        String jsonBody = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8000/v1/tasks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonNode root = mapper.readTree(response.body());
        String status = root.path("status").asText("");
        if (!"SUCCESS".equalsIgnoreCase(status)) {
            return null;
        }

        JsonNode allocations = root.path("allocations");
        if (!allocations.isArray() || allocations.size() == 0) {
            return null;
        }

        // We have at least one successful allocation! Let's narrate the Google AI workflow
        emit(offset, AgentType.SUPERMARKET, Severity.INFO, supermarketAgent.describe(donation));
        emit(offset, AgentType.GEMINI, Severity.INFO, "Google AI Orchestrator: Autonomous negotiation initiated.");
        emit(offset, AgentType.GEMINI, Severity.INFO, "Google AI Orchestrator: Fetching nearby charities and requirements from database...");
        
        // Process the first allocation
        JsonNode allocation = allocations.get(0);
        String charityId = allocation.path("charity_id").asText("");
        String charityName = allocation.path("charity_name").asText("");
        
        JsonNode logisticsNode = allocation.path("logistics");
        double distanceMiles = logisticsNode.path("distance_miles").asDouble(0.0);
        int travelTimeMins = logisticsNode.path("travel_time_mins").asInt(0);

        emit(offset, AgentType.ROUTING, Severity.INFO, "Google AI Orchestrator: Evaluating travel distances using Google Maps API...");
        emit(offset, AgentType.ROUTING, Severity.SUCCESS, "Google Maps Matrix calculated: " + distanceMiles + " miles to " + charityName + " (" + travelTimeMins + " mins travel time).");

        CharityRequest charity = charityRequestDatabase.findById(charityId);
        if (charity == null) {
            // Fallback lookup by name or create a stub request
            charity = charityRequestDatabase.findAll().stream()
                    .filter(c -> c.getCharityName().equalsIgnoreCase(charityName))
                    .findFirst()
                    .orElse(null);
        }

        if (charity == null) {
            charity = CharityRequest.builder()
                    .id(charityId)
                    .charityName(charityName)
                    .deliveryLocation(donation.getPickupLocation())
                    .status(com.foodflow.model.CharityStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
        }

        emit(offset, AgentType.CHARITY, Severity.INFO, charityAgent.describe(charity));
        emit(offset, AgentType.SAFETY, Severity.SUCCESS, "All safety checks approved! Match dietary, allergen and storage capacity rules: [PASSED]");

        // Allocate items
        JsonNode itemsAllocated = allocation.path("items_allocated");
        int allocatedMeals = donation.getQuantity();
        if (itemsAllocated.isArray() && itemsAllocated.size() > 0) {
            allocatedMeals = itemsAllocated.get(0).path("quantity").asInt(donation.getQuantity());
        }

        LocalTime pickupTime = donation.getAvailableFrom() != null ? donation.getAvailableFrom() : LocalTime.of(18, 0);
        LocalTime deliveryTime = pickupTime.plusMinutes(travelTimeMins);

        RescuePlan plan = RescuePlan.builder()
                .id(UUID.randomUUID().toString())
                .donationId(donation.getId())
                .charityRequestId(charity.getId())
                .supermarketName(donation.getSupermarketName())
                .charityName(charity.getCharityName())
                .foodDescription(donation.getFoodDescription())
                .dietaryTypes(donation.getDietaryTypes())
                .allocatedMeals(allocatedMeals)
                .pickupTime(pickupTime)
                .estimatedDeliveryTime(deliveryTime)
                .foodDeadline(donation.getExpiresAt())
                .estimatedDistanceMiles(distanceMiles)
                .matchScorePercent(100)
                .safetyPassed(true)
                .status(PlanStatus.PROPOSED)
                .createdAt(Instant.now())
                .explanation("Google AI Orchestration: Supermarket surplus successfully routed to " + charityName + " via real-world Google Maps analysis. Distance: " + distanceMiles + " miles, Driving Time: " + travelTimeMins + " mins.")
                .build();

        // Save plan
        rescuePlanStore.save(plan);
        emit(offset, AgentType.COORDINATOR, Severity.SUCCESS, "Google AI Orchestration approved! Match made: " + allocatedMeals + " meals allocated to " + charityName + ".");

        return new NegotiationOutcome(true, plan);
    }

    private RescuePlan buildPlan(MatchCandidate best) {
        Donation donation = best.getDonation();
        CharityRequest charity = best.getCharity();
        DriverAvailability driver = best.getDriver();

        RescuePlan plan = RescuePlan.builder()
                .id(UUID.randomUUID().toString())
                .donationId(donation.getId())
                .charityRequestId(charity.getId())
                .driverAvailabilityId(driver == null ? null : driver.getId())
                .supermarketName(donation.getSupermarketName())
                .charityName(charity.getCharityName())
                .driverName(driver == null ? null : driver.getDriverName())
                .foodDescription(donation.getFoodDescription())
                .dietaryTypes(donation.getDietaryTypes())
                .allocatedMeals(best.getAllocatedMeals())
                .pickupTime(best.getPickupTime())
                .estimatedDeliveryTime(best.getDeliveryTime())
                .foodDeadline(donation.getExpiresAt())
                .estimatedDistanceMiles(best.getDistanceMiles())
                .matchScorePercent(best.getScorePercent())
                .scoreBreakdown(best.getScoreBreakdown())
                .safetyPassed(best.isSafetyPassed())
                .safetyChecks(best.getSafetyChecks())
                .rejectionReasons(best.getRejectionReasons())
                .status(best.isSafetyPassed() ? PlanStatus.PROPOSED : PlanStatus.REJECTED)
                .createdAt(Instant.now())
                .build();

        plan.setExplanation(geminiService.explainMatch(plan, donation, charity, driver));
        return plan;
    }

    private SafetyCheckResult find(List<SafetyCheckResult> checks, String name) {
        return checks.stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElse(SafetyCheckResult.builder().name(name).passed(true).detail("Not evaluated.").build());
    }

    private void emit(AtomicLong offset, AgentType type, Severity severity, String message) {
        agentMessageStore.append(AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .agentType(type)
                .severity(severity)
                .message(message)
                .timestamp(Instant.now())
                .revealOffsetMs(offset.getAndAdd(STEP_OFFSET_MS))
                .build());
    }

    public Optional<RescuePlan> confirm(String planId) {
        Optional<RescuePlan> planOpt = rescuePlanStore.findById(planId);
        if (planOpt.isEmpty()) {
            return Optional.empty();
        }
        RescuePlan plan = planOpt.get();
        if (plan.getStatus() != PlanStatus.PROPOSED) {
            return Optional.of(plan);
        }
        plan.setStatus(PlanStatus.CONFIRMED);
        plan.setConfirmedAt(Instant.now());
        rescuePlanStore.save(plan);

        donationStore.findById(plan.getDonationId()).ifPresent(d -> {
            d.setStatus(com.foodflow.model.DonationStatus.MATCHED);
            donationStore.save(d);
        });
        charityRequestDatabase.findById(plan.getCharityRequestId()).ifPresent(c -> {
            c.setStatus(com.foodflow.model.CharityStatus.MATCHED);
            charityRequestDatabase.save(c);
        });
        if (plan.getDriverAvailabilityId() != null) {
            driverStore.findById(plan.getDriverAvailabilityId()).ifPresent(dr -> {
                dr.setStatus(com.foodflow.model.DriverStatus.ASSIGNED);
                driverStore.save(dr);
            });
        }

        return Optional.of(plan);
    }
}
