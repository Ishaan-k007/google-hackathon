package com.foodflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.FoodVisionItem;
import com.foodflow.model.FoodVisionResult;
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
import java.util.ArrayList;
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

    /**
     * Turns one free-text submission into a structured intent record for a given role
     * (supermarket/donor, charity, driver). If required information is missing or a
     * safety-relevant detail (e.g. an allergen) is ambiguous, returns complete=false with
     * a single clarification question instead of guessing - the caller is expected to show
     * that question to the user, collect a reply, and call again with forceFinal=true.
     *
     * Gemini decides what's missing and phrases the question; it never invents facts that
     * weren't stated (e.g. it will not assert an allergen is present or absent unless told).
     */
    public IntentResult interpretIntent(String role, String text, boolean forceFinal) {
        String safeText = text == null ? "" : text;
        IntentResult fallback = fallbackIntent(role, safeText, forceFinal);
        if (!isConfigured() || safeText.isBlank()) {
            return fallback;
        }
        try {
            JsonNode node = callGeminiJson(intentPrompt(role, safeText, forceFinal));
            boolean complete = forceFinal || node.path("complete").asBoolean(true);
            String question = node.path("clarificationQuestion").isNull() ? null
                    : node.path("clarificationQuestion").asText(null);
            Map<String, Object> data = objectMapper.convertValue(node.get("data"), LinkedHashMap.class);
            return IntentResult.builder()
                    .complete(complete)
                    .clarificationQuestion(complete ? null : question)
                    .data(data != null ? data : fallback.getData())
                    .source("gemini")
                    .build();
        } catch (Exception e) {
            log.warn("Gemini interpretIntent() failed for role={}, falling back: {}", role, e.toString());
            return fallback;
        }
    }

    private IntentResult fallbackIntent(String role, String text, boolean forceFinal) {
        return switch (role) {
            case "supermarket" -> FallbackInterpreter.donorIntent(text, forceFinal);
            case "charity" -> FallbackInterpreter.charityIntent(text, forceFinal);
            case "driver" -> FallbackInterpreter.driverIntent(text, forceFinal);
            default -> IntentResult.builder().complete(true).data(Map.of()).source("fallback").build();
        };
    }

    /**
     * Turns an arbitrary match-result JSON (from an external agentic workflow this app does
     * not control, so its shape isn't fixed) into a friendly, second-person alert message for
     * whichever foodbank/charity it names, plus a best-effort guess at that name for display.
     * Never invents facts beyond what the JSON contains.
     */
    public AlertGeneration generateFoodbankAlert(JsonNode json) {
        AlertGeneration fallback = FallbackInterpreter.alertFromJson(json);
        if (!isConfigured()) {
            return fallback;
        }
        try {
            String prompt = "Below is a JSON record describing a food-rescue match, produced by an external "
                    + "matching system - its field names may vary and you must not assume a fixed schema. "
                    + "1) Identify the name of the foodbank/charity being notified (look for anything like a "
                    + "charity, foodbank or recipient name field; use null if you genuinely cannot find one). "
                    + "2) Write a warm, clear alert message of 2-4 sentences addressed directly to that foodbank "
                    + "in second person (e.g. \"You've been matched with...\"), summarizing what is being "
                    + "donated, by whom, the quantity, and pickup/delivery timing if present in the JSON. State "
                    + "only facts present in the JSON - never invent or assume anything not there. "
                    + "Respond ONLY as JSON: {\"foodbankName\": string or null, \"message\": string}\n\n"
                    + "JSON:\n" + json.toString();
            JsonNode node = callGeminiJson(prompt);
            String name = node.path("foodbankName").isNull() ? null : node.path("foodbankName").asText(null);
            String message = node.path("message").asText(null);
            if (message == null || message.isBlank()) {
                return fallback;
            }
            return AlertGeneration.builder().foodbankName(name != null ? name : fallback.getFoodbankName())
                    .message(message).build();
        } catch (Exception e) {
            log.warn("Gemini generateFoodbankAlert() failed, falling back: {}", e.toString());
            return fallback;
        }
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

    /**
     * Identifies the food visible in a donation photo (e.g. taken on the supermarket portal's
     * camera capture tab). Unlike the other methods above, there is no deterministic offline
     * fallback for image recognition - if Gemini isn't configured or the call fails, this
     * returns success=false with a human-readable error instead of guessing.
     */
    public FoodVisionResult analyzeFoodImage(String base64Image, String mimeType) {
        if (!isConfigured()) {
            return FoodVisionResult.builder().success(false)
                    .error("Gemini API key is not configured, so photo recognition is unavailable.")
                    .build();
        }
        if (base64Image == null || base64Image.isBlank()) {
            return FoodVisionResult.builder().success(false).error("No image was received.").build();
        }
        try {
            JsonNode node = callGeminiVisionJson(visionPrompt(), base64Image,
                    (mimeType == null || mimeType.isBlank()) ? "image/jpeg" : mimeType);

            List<FoodVisionItem> items = new ArrayList<>();
            for (JsonNode itemNode : node.path("items")) {
                items.add(FoodVisionItem.builder()
                        .name(itemNode.path("name").asText(null))
                        .category(itemNode.path("category").asText(null))
                        .estimatedQuantity(itemNode.path("estimatedQuantity").isNumber()
                                ? itemNode.path("estimatedQuantity").asInt() : null)
                        .dietaryTypes(toStringList(itemNode.path("dietaryTypes")))
                        .likelyAllergens(toStringList(itemNode.path("likelyAllergens")))
                        .confidence(itemNode.path("confidence").isNumber()
                                ? itemNode.path("confidence").asDouble() : null)
                        .build());
            }
            String summary = node.path("summary").asText(null);
            if (items.isEmpty() && (summary == null || summary.isBlank())) {
                return FoodVisionResult.builder().success(false)
                        .error("Gemini couldn't identify any food in that photo. Try a clearer, closer shot.")
                        .build();
            }
            return FoodVisionResult.builder().success(true).items(items).summary(summary).build();
        } catch (Exception e) {
            log.warn("Gemini analyzeFoodImage() failed: {}", e.toString());
            return FoodVisionResult.builder().success(false)
                    .error("Gemini couldn't analyze that photo: " + e.getMessage()).build();
        }
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode n : arrayNode) list.add(n.asText());
        }
        return list;
    }

    private String visionPrompt() {
        return "You are looking at a photo of surplus food for a food-rescue donation app. Identify each distinct "
                + "food item or dish visible. Always give your best-effort guess for every field - never refuse - "
                + "but reflect uncertainty honestly in the confidence score. Common allergen categories to consider: "
                + "nuts, gluten, shellfish, egg, soy, sesame, fish, dairy. Dietary types to consider: vegetarian, "
                + "vegan, halal, contains_meat, contains_dairy, gluten_free.\n\n"
                + "Respond ONLY as JSON in this exact shape: {\"items\": [{\"name\": string, \"category\": string "
                + "(snake_case, e.g. \\\"bakery_item\\\", \\\"prepared_meal\\\", \\\"fruit\\\", \\\"vegetable\\\", "
                + "\\\"dairy\\\", \\\"canned_good\\\", \\\"beverage\\\"), \"estimatedQuantity\": number or null "
                + "(count of visible units/portions), \"dietaryTypes\": [string], \"likelyAllergens\": [string], "
                + "\"confidence\": number between 0 and 1}], \"summary\": string (one plain-English sentence "
                + "describing everything in the photo the way a supermarket employee would phrase it for a "
                + "donation form, e.g. \\\"We have about 12 loaves of bread and 20 fruit cups, available now.\\\")}";
    }

    private JsonNode callGeminiVisionJson(String prompt, String base64Image, String mimeType)
            throws IOException, InterruptedException {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("responseMimeType", "application/json");

        Map<String, Object> imagePart = Map.of("inlineData", Map.of("mimeType", mimeType, "data", base64Image));
        Map<String, Object> textPart = Map.of("text", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(imagePart, textPart))));
        body.put("generationConfig", generationConfig);

        String requestJson = objectMapper.writeValueAsString(body);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;

        // Image analysis is slower than plain-text calls, so this request gets its own longer
        // timeout rather than reusing the configured text-call timeout.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(25))
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
        return objectMapper.readTree(textNode.asText());
    }

    private String fallbackExplanation(RescuePlan plan, Donation donation, CharityRequest charity, DriverAvailability driver) {
        if (plan.isSafetyPassed()) {
            return "This plan was selected because the meals meet " + plan.getCharityName() + "'s dietary requirements, "
                    + (plan.getDriverName() != null ? "the volunteer has sufficient capacity and travel range, " : "collection is handled directly by the charity, ")
                    + "and the delivery can be completed before the food's deadline of " + plan.getFoodDeadline() + ".";
        }
        return "This match was rejected because it failed one or more safety checks: " + String.join("; ", plan.getRejectionReasons()) + ".";
    }

    private String intentPrompt(String role, String text, boolean forceFinal) {
        String dataSchema = switch (role) {
            case "supermarket" -> "{\"donor_name\": string, \"location\": string, \"available_from\": \"HH:mm\", "
                    + "\"surplus_items\": [{\"item_type\": string (snake_case category, e.g. \\\"vegetarian_meal\\\", "
                    + "\\\"bakery_item\\\"), \"quantity\": number, \"expires_at\": \"HH:mm\", "
                    + "\"allergens\": [string] (use [\\\"none\\\"] if explicitly none), "
                    + "\"storage_req\": \"ambient\"|\"refrigerated\"|\"frozen\"}]}";
            case "charity" -> "{\"charity_name\": string, \"location\": string, \"needed_by\": \"HH:mm\", "
                    + "\"can_collect\": boolean, \"requested_items\": [{\"item_type\": string (snake_case category), "
                    + "\"quantity\": number, \"rejected_allergens\": [string] (hard constraints, [] if none stated), "
                    + "\"storage_capability\": \"ambient\"|\"refrigerated\"|\"frozen\"|\"none\"}]}";
            case "driver" -> "{\"driver_name\": string, \"location\": string, \"available_from\": \"HH:mm\", "
                    + "\"available_until\": \"HH:mm\" or null, \"max_distance_miles\": number, \"capacity\": number, "
                    + "\"insulated_storage\": boolean}";
            default -> "{}";
        };

        String completenessRule = switch (role) {
            case "supermarket" -> "Required: donor_name, available_from, and at least one surplus item with "
                    + "item_type, quantity and expires_at. Also require clarification if the item plausibly contains "
                    + "a common allergen (dairy, nuts, gluten, egg, soy, sesame, shellfish, fish) that the text "
                    + "neither confirms nor rules out - ask about the single most likely one, do not assert it is present.";
            case "charity" -> "Required: charity_name, needed_by, and at least one requested item with item_type "
                    + "and quantity. rejected_allergens and can_collect may default to [] / true if genuinely unstated - "
                    + "do not ask about those unless quantity or needed_by is also missing.";
            case "driver" -> "Required: driver_name, available_from, capacity, and max_distance_miles.";
            default -> "";
        };

        String location = "\"location\" should be your best-effort \"latitude,longitude\" estimate if the text "
                + "names a real, geocodable place; otherwise put the place name/description as plain text; use null "
                + "only if truly nothing was said about location. This is a logistics estimate, not a safety fact, "
                + "so a reasonable best guess is fine and does not need a clarification question.";

        String finalityRule = forceFinal
                ? "The user has already been asked once and given a follow-up answer - do NOT ask again. Always "
                        + "return complete=true and fill \"data\" with your best interpretation of everything given, "
                        + "even if something is still a little uncertain."
                : "If any required field is missing or a safety-relevant detail is ambiguous per the rule above, "
                        + "return complete=false and ONE short, specific clarificationQuestion (never invent the "
                        + "missing fact instead of asking). Otherwise return complete=true and clarificationQuestion=null.";

        return "You extract structured facts from a " + role + " participant's free-text description for a "
                + "food-rescue coordination app. You NEVER invent or assume safety-relevant facts (allergens, "
                + "expiry, quantities) that were not stated - if something important is missing, ask instead of "
                + "guessing. Non-safety logistics fields (like location coordinates) may be a reasonable best-effort "
                + "estimate. " + completenessRule + " " + location + " " + finalityRule + " Times must be 24-hour "
                + "\"HH:mm\". Omit fields you truly cannot infer (use null) rather than guessing wildly.\n\n"
                + "Respond ONLY as JSON in this exact shape: "
                + "{\"complete\": boolean, \"clarificationQuestion\": string or null, \"data\": " + dataSchema + "}"
                + "\n\nDescription: \"" + text + "\"";
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
