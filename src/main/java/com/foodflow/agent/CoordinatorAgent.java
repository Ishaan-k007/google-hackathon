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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
