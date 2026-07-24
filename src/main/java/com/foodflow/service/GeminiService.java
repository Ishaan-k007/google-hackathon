package com.foodflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.RescuePlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps Gemini for the three narrow jobs it is trusted with in FoodFlow:
 * natural-language interpretation, missing-information detection and plain-English
 * match explanation. It NEVER makes a safety or matching decision - those stay in
 * SafetyAgent / RoutingMatchingAgent as deterministic rules.
 *
 * Every call has a deterministic, offline fallback so a flaky network or a missing
 * API key never blocks the live demo.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiService(@Value("${gemini.api-key:}") String apiKey,
                          @Value("${gemini.model:gemini-flash-latest}") String model,
                          @Value("${gemini.timeout-seconds:8}") int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Natural-language -> structured constraints, used by each portal's "Interpret with Gemini" button. */
    public Map<String, Object> interpret(String role, String text) {
        Map<String, Object> result;
        boolean usedGemini = false;
        try {
            if (isConfigured() && text != null && !text.isBlank()) {
                JsonNode node = callGeminiJson(interpretPrompt(role, text));
                result = objectMapper.convertValue(node, LinkedHashMap.class);
                usedGemini = true;
            } else {
                result = fallbackInterpret(role, text);
            }
        } catch (Exception e) {
            log.warn("Gemini interpret() failed for role={}, falling back: {}", role, e.toString());
            result = fallbackInterpret(role, text);
        }
        result.put("_source", usedGemini ? "gemini" : "fallback");
        return result;
    }

    private Map<String, Object> fallbackInterpret(String role, String text) {
        String safeText = text == null ? "" : text;
        return switch (role) {
            case "supermarket" -> FallbackInterpreter.interpretSupermarket(safeText);
            case "charity" -> FallbackInterpreter.interpretCharity(safeText);
            case "driver" -> FallbackInterpreter.interpretDriver(safeText);
            default -> new LinkedHashMap<>();
        };
    }

    /** Flags one plausible missing safety detail about a donation (e.g. an unmentioned allergen). Narrative only. */
    public String detectDonationClarification(Donation donation) {
        String combinedText = (nullToEmpty(donation.getFoodDescription()) + " " + nullToEmpty(donation.getAdditionalNotes())).trim();
        List<String> declared = donation.getAllergens();

        if (!isConfigured()) {
            return FallbackInterpreter.detectAllergenClarification(declared, combinedText);
        }
        try {
            String prompt = "A supermarket declared these allergens as PRESENT in a food donation: " + declared
                    + ". The free-text description is: \"" + combinedText + "\". "
                    + "Common allergen categories to consider: nuts, gluten, shellfish, egg, soy, sesame, fish, dairy. "
                    + "If there is a common allergen category that was neither declared as present nor explicitly "
                    + "ruled out in the text, write ONE short clarification question a coordinator could ask the "
                    + "supermarket to confirm it (e.g. asking about nuts when only dairy was mentioned). "
                    + "Do not claim the allergen IS present - only ask. If nothing is missing, return an empty string. "
                    + "Respond as JSON: {\"clarificationQuestion\": \"...\" or null}";
            JsonNode node = callGeminiJson(prompt);
            JsonNode q = node.get("clarificationQuestion");
            if (q != null && !q.isNull() && !q.asText().isBlank()) {
                return q.asText();
            }
            return null;
        } catch (Exception e) {
            log.warn("Gemini detectDonationClarification() failed, falling back: {}", e.toString());
            return FallbackInterpreter.detectAllergenClarification(declared, combinedText);
        }
    }

    /** Plain-English explanation of why a deterministic match was (or wasn't) approved. */
    public String explainMatch(RescuePlan plan, Donation donation, CharityRequest charity, DriverAvailability driver) {
        String fallback = fallbackExplanation(plan, donation, charity, driver);
        if (!isConfigured()) {
            return fallback;
        }
        try {
            String prompt = "You explain food-rescue logistics decisions in plain, warm, concise English (2-3 sentences, "
                    + "no bullet points, no markdown). A deterministic safety and matching system already made the "
                    + "decision below - you are only explaining it, not deciding it. Never invent facts not given here.\n\n"
                    + "Outcome: " + (plan.isSafetyPassed() ? "APPROVED" : "REJECTED") + "\n"
                    + "Donation: " + plan.getAllocatedMeals() + " meals (" + String.join(", ", nullToEmptyList(plan.getDietaryTypes()))
                    + ") from " + plan.getSupermarketName() + "\n"
                    + "Charity: " + plan.getCharityName() + (charity != null ? ", requested " + charity.getRequestedQuantity() + " meals" : "") + "\n"
                    + "Driver: " + (plan.getDriverName() == null ? "none needed (charity self-collects)" : plan.getDriverName()) + "\n"
                    + "Pickup: " + plan.getPickupTime() + ", estimated delivery: " + plan.getEstimatedDeliveryTime()
                    + ", food deadline: " + plan.getFoodDeadline() + "\n"
                    + "Distance: " + plan.getEstimatedDistanceMiles() + " miles\n"
                    + "Safety checks: " + plan.getSafetyChecks() + "\n"
                    + (plan.isSafetyPassed() ? "" : "Rejection reasons: " + plan.getRejectionReasons() + "\n")
                    + "\nWrite the explanation now as plain text (not JSON).";
            String text = callGeminiText(prompt);
            return (text == null || text.isBlank()) ? fallback : text.trim();
        } catch (Exception e) {
            log.warn("Gemini explainMatch() failed, falling back: {}", e.toString());
            return fallback;
        }
    }

    private String fallbackExplanation(RescuePlan plan, Donation donation, CharityRequest charity, DriverAvailability driver) {
        if (plan.isSafetyPassed()) {
            return "This plan was selected because the meals meet " + plan.getCharityName() + "'s dietary requirements, "
                    + (plan.getDriverName() != null ? "the volunteer has sufficient capacity and travel range, " : "collection is handled directly by the charity, ")
                    + "and the delivery can be completed before the food's deadline of " + plan.getFoodDeadline() + ".";
        }
        return "This match was rejected because it failed one or more safety checks: " + String.join("; ", plan.getRejectionReasons()) + ".";
    }

    private String interpretPrompt(String role, String text) {
        String schema = switch (role) {
            case "supermarket" -> "{\"quantity\": number, \"dietaryTypes\": [string], \"allergens\": [string], "
                    + "\"storageType\": \"AMBIENT\"|\"CHILLED\"|\"FROZEN\", \"availableFrom\": \"HH:mm\", "
                    + "\"expiresAt\": \"HH:mm\", \"clarificationQuestion\": string or null}";
            case "charity" -> "{\"requestedQuantity\": number, \"acceptedDietaryTypes\": [string], "
                    + "\"rejectedAllergens\": [string], \"latestDeliveryTime\": \"HH:mm\", \"canCollect\": boolean, "
                    + "\"hardConstraints\": [string], \"preferences\": [string], \"clarificationQuestion\": string or null}";
            case "driver" -> "{\"availableFrom\": \"HH:mm\", \"availableUntil\": \"HH:mm\" or null, "
                    + "\"maximumDistanceMiles\": number, \"capacityMeals\": number, \"hasInsulatedStorage\": boolean or null}";
            default -> "{}";
        };
        return "Extract structured facts from this " + role + " participant's description for a food-rescue "
                + "coordination app. Distinguish HARD CONSTRAINTS (things stated as absolute requirements, e.g. "
                + "\"we cannot accept nuts\") from PREFERENCES (things stated as nice-to-have, e.g. \"vegetarian "
                + "would be easier\") when the schema asks for them. Only include fields you can infer with "
                + "reasonable confidence; omit fields you cannot determine - never invent allergen or safety facts "
                + "that were not stated. Times must be 24-hour \"HH:mm\". "
                + "Respond ONLY as JSON matching this shape: " + schema + "\n\nDescription: \"" + text + "\"";
    }

    private JsonNode callGeminiJson(String prompt) throws IOException, InterruptedException {
        String responseText = callGemini(prompt, true);
        return objectMapper.readTree(responseText);
    }

    private String callGeminiText(String prompt) throws IOException, InterruptedException {
        return callGemini(prompt, false);
    }

    private String callGemini(String prompt, boolean asJson) throws IOException, InterruptedException {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", asJson ? 0.1 : 0.4);
        // Generous headroom: this model spends a chunk of maxOutputTokens on internal
        // "thinking" tokens before it ever writes the visible answer, so a tight budget
        // truncates the real output before it starts.
        generationConfig.put("maxOutputTokens", 2048);
        if (asJson) {
            generationConfig.put("responseMimeType", "application/json");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", generationConfig);

        String requestJson = objectMapper.writeValueAsString(body);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Gemini API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.isNull()) {
            throw new IOException("Gemini response had no text content: " + response.body());
        }
        return textNode.asText();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static List<String> nullToEmptyList(List<String> list) {
        return list == null ? List.of() : list;
    }
}
