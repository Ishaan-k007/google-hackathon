package com.foodflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, offline stand-in for Gemini's natural-language interpretation.
 * Used only when the Gemini API is not configured or a call fails, so the
 * demo never stalls waiting on a network call.
 */
final class FallbackInterpreter {

    private FallbackInterpreter() {
    }

    static Map<String, Object> interpretSupermarket(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        int qty = KeywordExtractor.findQuantity(text);
        if (qty > 0) {
            result.put("quantity", qty);
        }
        List<String> dietary = KeywordExtractor.findDietaryTypes(text);
        if (!dietary.isEmpty()) {
            result.put("dietaryTypes", dietary);
        }
        List<String> allergens = KeywordExtractor.findAllergens(text);
        if (!allergens.isEmpty()) {
            result.put("allergens", allergens);
        }
        String[] times = findAvailableAndDeadline(text);
        if (times[0] != null) {
            result.put("availableFrom", times[0]);
        }
        if (times[1] != null) {
            result.put("expiresAt", times[1]);
        }
        String clarification = detectAllergenClarification(allergens, text);
        if (clarification != null) {
            result.put("clarificationQuestion", clarification);
        }
        return result;
    }

    static Map<String, Object> interpretCharity(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        int qty = KeywordExtractor.findQuantity(text);
        if (qty > 0) {
            result.put("requestedQuantity", qty);
        }
        List<String> dietary = KeywordExtractor.findDietaryTypes(text);
        if (!dietary.isEmpty()) {
            result.put("acceptedDietaryTypes", dietary);
        }

        List<String> hardConstraints = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        String lower = text.toLowerCase();
        for (String allergen : KeywordExtractor.KNOWN_ALLERGENS) {
            if ((lower.contains("cannot accept " + allergen) || lower.contains("can't accept " + allergen)
                    || lower.contains("no " + allergen) || lower.contains("without " + allergen))
                    && !rejected.contains(allergen)) {
                rejected.add(allergen.equals("eggs") ? "egg" : allergen.equals("peanuts") ? "nuts" : allergen);
            }
        }
        if (!rejected.isEmpty()) {
            result.put("rejectedAllergens", rejected);
            for (String a : rejected) {
                hardConstraints.add("Cannot accept " + a);
            }
        }

        boolean cannotCollect = KeywordExtractor.mentionsNegativeCollection(text);
        result.put("canCollect", !cannotCollect);
        if (cannotCollect) {
            hardConstraints.add("No transport available to collect");
        }

        String time = KeywordExtractor.findTime(text);
        if (time != null) {
            result.put("latestDeliveryTime", time);
            hardConstraints.add("Delivery required before " + time);
        }

        if (!dietary.isEmpty()) {
            result.put("preferences", List.of(String.join(", ", dietary) + " meals would be easier to distribute"));
        }
        if (!hardConstraints.isEmpty()) {
            result.put("hardConstraints", hardConstraints);
        }
        return result;
    }

    static Map<String, Object> interpretDriver(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        String time = KeywordExtractor.findTime(text);
        if (time != null) {
            result.put("availableFrom", time);
        }
        double distance = KeywordExtractor.findDistanceMiles(text);
        if (distance > 0) {
            result.put("maximumDistanceMiles", distance);
        }
        int capacity = KeywordExtractor.findQuantity(text);
        if (capacity > 0) {
            result.put("capacityMeals", capacity);
        }
        String lower = text.toLowerCase();
        if (lower.contains("insulated") || lower.contains("refrigerat") || lower.contains("chilled")) {
            result.put("hasInsulatedStorage", true);
        }
        return result;
    }

    static String detectAllergenClarification(List<String> declaredAllergens, String text) {
        if (declaredAllergens == null || declaredAllergens.isEmpty()) {
            return null;
        }
        for (String candidate : KeywordExtractor.ALLERGEN_CLARIFICATION_PRIORITY) {
            if (!declaredAllergens.contains(candidate) && !text.toLowerCase().contains(candidate)) {
                String declared = String.join(" and ", declaredAllergens);
                return "The meals contain " + declared + ", but no information was provided about " + candidate
                        + ". Can the supermarket confirm whether they contain " + candidate + "?";
            }
        }
        return null;
    }

    private static String[] findAvailableAndDeadline(String text) {
        // Looks for "from X" and "before/until Y" patterns independently.
        String[] result = new String[2];
        java.util.regex.Matcher fromM = java.util.regex.Pattern
                .compile("from\\s+((?:\\d{1,2}[:.]?\\d{0,2}\\s?(?:am|pm)?)|half\\s*(?:past\\s*)?\\w+)")
                .matcher(text.toLowerCase());
        if (fromM.find()) {
            result[0] = KeywordExtractor.findTime(fromM.group(1));
        }
        java.util.regex.Matcher beforeM = java.util.regex.Pattern
                .compile("(?:before|until|by)\\s+((?:\\d{1,2}[:.]?\\d{0,2}\\s?(?:am|pm)?)|half\\s*(?:past\\s*)?\\w+)")
                .matcher(text.toLowerCase());
        if (beforeM.find()) {
            result[1] = KeywordExtractor.findTime(beforeM.group(1));
        }
        if (result[0] == null || result[1] == null) {
            // Fall back to just grabbing any time mentions in order of appearance.
            java.util.regex.Matcher any = java.util.regex.Pattern
                    .compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").matcher(text);
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
