package com.foodflow.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, offline stand-in for Gemini's natural-language interpretation.
 * Used only when the Gemini API is not configured or a call fails, so intent
 * capture never stalls waiting on a network call. Quality is intentionally
 * best-effort here - Gemini is the primary path.
 */
final class FallbackInterpreter {

    private FallbackInterpreter() {
    }

    static IntentResult donorIntent(String text, boolean forceFinal) {
        List<String> allergens = KeywordExtractor.findAllergens(text);
        List<String> dietary = KeywordExtractor.findDietaryTypes(text);
        int quantity = KeywordExtractor.findQuantity(text);
        String[] times = findAvailableAndDeadline(text);
        String itemType = guessItemType(dietary);
        String storageReq = guessStorageReq(text);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item_type", itemType);
        item.put("quantity", quantity > 0 ? quantity : null);
        item.put("expires_at", times[1]);
        item.put("allergens", allergens.isEmpty() ? List.of("none") : allergens);
        item.put("storage_req", storageReq);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("donor_name", guessName(text));
        data.put("location", null);
        data.put("available_from", times[0]);
        data.put("surplus_items", List.of(item));

        String question = null;
        if (quantity <= 0) {
            question = "How many meals (or items) are available?";
        } else if (times[1] == null) {
            question = "What time must this food be collected by (its expiry / safe-collection deadline)?";
        } else if (times[0] == null) {
            question = "What time is the food available for collection from?";
        } else {
            question = FallbackInterpreter.detectAllergenClarification(allergens, text);
        }

        boolean complete = forceFinal || question == null;
        return IntentResult.builder().complete(complete).clarificationQuestion(complete ? null : question)
                .data(data).source("fallback").build();
    }

    static IntentResult charityIntent(String text, boolean forceFinal) {
        int quantity = KeywordExtractor.findQuantity(text);
        List<String> dietary = KeywordExtractor.findDietaryTypes(text);
        String itemType = guessItemType(dietary);
        String time = KeywordExtractor.findTime(text);
        boolean cannotCollect = KeywordExtractor.mentionsNegativeCollection(text);

        List<String> rejected = new ArrayList<>();
        String lower = text.toLowerCase();
        for (String allergen : KeywordExtractor.KNOWN_ALLERGENS) {
            if ((lower.contains("cannot accept " + allergen) || lower.contains("can't accept " + allergen)
                    || lower.contains("no " + allergen) || lower.contains("without " + allergen))
                    && !rejected.contains(allergen)) {
                rejected.add(allergen.equals("eggs") ? "egg" : allergen.equals("peanuts") ? "nuts" : allergen);
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item_type", itemType);
        item.put("quantity", quantity > 0 ? quantity : null);
        item.put("rejected_allergens", rejected);
        item.put("storage_capability", guessStorageReq(text));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("charity_name", guessName(text));
        data.put("location", null);
        data.put("needed_by", time);
        data.put("can_collect", !cannotCollect);
        data.put("requested_items", List.of(item));

        String question = null;
        if (quantity <= 0) {
            question = "How many meals (or items) do you need?";
        } else if (time == null) {
            question = "What is the latest time the food must be delivered by?";
        }

        boolean complete = forceFinal || question == null;
        return IntentResult.builder().complete(complete).clarificationQuestion(complete ? null : question)
                .data(data).source("fallback").build();
    }

    static IntentResult driverIntent(String text, boolean forceFinal) {
        String time = KeywordExtractor.findTime(text);
        double distance = KeywordExtractor.findDistanceMiles(text);
        int capacity = KeywordExtractor.findQuantity(text);
        String lower = text.toLowerCase();
        boolean insulated = lower.contains("insulated") || lower.contains("refrigerat") || lower.contains("chilled");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("driver_name", guessName(text));
        data.put("location", null);
        data.put("available_from", time);
        data.put("available_until", null);
        data.put("max_distance_miles", distance > 0 ? distance : null);
        data.put("capacity", capacity > 0 ? capacity : null);
        data.put("insulated_storage", insulated);

        String question = null;
        if (time == null) {
            question = "What time are you available from?";
        } else if (capacity <= 0) {
            question = "How many meals (or items) can you carry?";
        } else if (distance <= 0) {
            question = "How far are you able to travel?";
        }

        boolean complete = forceFinal || question == null;
        return IntentResult.builder().complete(complete).clarificationQuestion(complete ? null : question)
                .data(data).source("fallback").build();
    }

    private static final List<String> NAME_FIELD_CANDIDATES = List.of(
            "charity_name", "foodbank_name", "food_bank_name", "recipient_name", "organisation_name",
            "organization_name", "charityName", "foodbankName", "recipientName", "name");

    static AlertGeneration alertFromJson(JsonNode root) {
        String name = findFirstFieldDeep(root, NAME_FIELD_CANDIDATES, 0, 3);
        StringBuilder sb = new StringBuilder();
        sb.append(name != null ? "Update for " + name + ": " : "FoodFlow update: ");
        sb.append(flattenToSentence(root));
        return AlertGeneration.builder().foodbankName(name).message(sb.toString()).build();
    }

    private static String findFirstFieldDeep(JsonNode node, List<String> candidateKeys, int depth, int maxDepth) {
        if (node == null || node.isNull() || depth > maxDepth) {
            return null;
        }
        if (node.isObject()) {
            for (String key : candidateKeys) {
                JsonNode v = node.get(key);
                if (v != null && v.isTextual() && !v.asText().isBlank()) {
                    return v.asText();
                }
            }
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String found = findFirstFieldDeep(node.get(fieldNames.next()), candidateKeys, depth + 1, maxDepth);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findFirstFieldDeep(child, candidateKeys, depth + 1, maxDepth);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String flattenToSentence(JsonNode node) {
        List<String> parts = new ArrayList<>();
        flattenInto(node, "", parts);
        return String.join("; ", parts) + ".";
    }

    private static void flattenInto(JsonNode node, String prefix, List<String> parts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String label = humanizeKey(e.getKey());
                flattenInto(e.getValue(), prefix.isEmpty() ? label : prefix + " " + label, parts);
            }
        } else if (node.isArray()) {
            int i = 1;
            for (JsonNode child : node) {
                flattenInto(child, prefix + " #" + i, parts);
                i++;
            }
        } else {
            parts.add(prefix + ": " + node.asText());
        }
    }

    private static String humanizeKey(String key) {
        String spaced = key.replace("_", " ");
        return spaced.isEmpty() ? spaced : Character.toLowerCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static String detectAllergenClarification(List<String> declaredAllergens, String text) {
        if (declaredAllergens == null || declaredAllergens.isEmpty()) {
            return null;
        }
        for (String candidate : KeywordExtractor.ALLERGEN_CLARIFICATION_PRIORITY) {
            if (!declaredAllergens.contains(candidate) && !text.toLowerCase().contains(candidate)) {
                String declared = String.join(" and ", declaredAllergens);
                return "The food contains " + declared + ", but no information was provided about " + candidate
                        + ". Can you confirm whether it contains " + candidate + "?";
            }
        }
        return null;
    }

    private static String guessItemType(List<String> dietary) {
        if (dietary.isEmpty()) {
            return "meal";
        }
        return dietary.get(0).replace("-", "_") + "_meal";
    }

    private static String guessStorageReq(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("frozen") || lower.contains("freezer")) {
            return "frozen";
        }
        if (lower.contains("chilled") || lower.contains("refrigerat") || lower.contains("fridge") || lower.contains("dairy")) {
            return "refrigerated";
        }
        return "ambient";
    }

    private static String guessName(String text) {
        Matcher m = Pattern.compile("^([A-Z][A-Za-z&'.]*(?:\\s[A-Z][A-Za-z&'.]*){0,3})\\s+(?:has|is|are|will|can|needs?|offers?)")
                .matcher(text.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String[] findAvailableAndDeadline(String text) {
        // Looks for "from X" and "before/until Y" patterns independently.
        String[] result = new String[2];
        Matcher fromM = Pattern
                .compile("from\\s+((?:\\d{1,2}[:.]?\\d{0,2}\\s?(?:am|pm)?)|half\\s*(?:past\\s*)?\\w+)")
                .matcher(text.toLowerCase());
        if (fromM.find()) {
            result[0] = KeywordExtractor.findTime(fromM.group(1));
        }
        Matcher beforeM = Pattern
                .compile("(?:before|until|by)\\s+((?:\\d{1,2}[:.]?\\d{0,2}\\s?(?:am|pm)?)|half\\s*(?:past\\s*)?\\w+)")
                .matcher(text.toLowerCase());
        if (beforeM.find()) {
            result[1] = KeywordExtractor.findTime(beforeM.group(1));
        }
        if (result[0] == null || result[1] == null) {
            Matcher any = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").matcher(text);
            List<String> found = new ArrayList<>();
            while (any.find()) {
                found.add(String.format("%02d:%02d", Integer.parseInt(any.group(1)), Integer.parseInt(any.group(2))));
            }
            if (result[0] == null && found.size() > 0) {
                result[0] = found.get(0);
            }
            if (result[1] == null && found.size() > 1) {
                result[1] = found.get(1);
            }
        }
        return result;
    }
}
