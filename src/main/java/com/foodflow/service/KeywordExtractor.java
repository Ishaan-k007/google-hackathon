package com.foodflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, dependency-free natural-language helpers used ONLY as a deterministic
 * fallback when the Gemini API is unavailable or returns something unusable.
 * Not intended to be a general NLP engine - just enough to keep the demo
 * working offline.
 */
final class KeywordExtractor {

    private KeywordExtractor() {
    }

    static final List<String> KNOWN_ALLERGENS = List.of(
            "nuts", "peanuts", "dairy", "gluten", "egg", "eggs", "soy", "shellfish", "sesame", "fish");

    static final List<String> ALLERGEN_CLARIFICATION_PRIORITY = List.of(
            "nuts", "gluten", "shellfish", "egg", "soy", "sesame", "fish");

    static final List<String> KNOWN_DIETARY_TYPES = List.of(
            "vegetarian", "vegan", "halal", "kosher", "gluten-free", "gluten free", "dairy-free", "dairy free");

    private static final Map<String, Integer> NUMBER_WORDS = new LinkedHashMap<>();

    static {
        String[] units = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
                "nineteen"};
        for (int i = 0; i < units.length; i++) {
            NUMBER_WORDS.put(units[i], i);
        }
        String[] tens = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < tens.length; i++) {
            NUMBER_WORDS.put(tens[i], (i + 2) * 10);
        }
    }

    static List<String> findAllergens(String text) {
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String allergen : KNOWN_ALLERGENS) {
            if (lower.contains(allergen)) {
                String normalized = allergen.equals("eggs") ? "egg" : allergen.equals("peanuts") ? "nuts" : allergen;
                if (!found.contains(normalized)) {
                    found.add(normalized);
                }
            }
        }
        return found;
    }

    static List<String> findDietaryTypes(String text) {
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String type : KNOWN_DIETARY_TYPES) {
            if (lower.contains(type)) {
                String normalized = type.replace(" ", "-");
                if (!found.contains(normalized)) {
                    found.add(normalized);
                }
            }
        }
        return found;
    }

    /** Finds the first standalone integer in text, optionally near "meal(s)". Returns -1 if none found. */
    static int findQuantity(String text) {
        Matcher near = Pattern.compile("(\\d{1,4})\\s*(vegetarian|vegan|halal|meals|meal)").matcher(text.toLowerCase());
        if (near.find()) {
            return Integer.parseInt(near.group(1));
        }
        Matcher digits = Pattern.compile("\\b(\\d{1,4})\\b").matcher(text);
        if (digits.find()) {
            return Integer.parseInt(digits.group(1));
        }
        Integer word = findNumberWord(text);
        return word == null ? -1 : word;
    }

    static double findDistanceMiles(String text) {
        Matcher digits = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*mile").matcher(text.toLowerCase());
        if (digits.find()) {
            return Double.parseDouble(digits.group(1));
        }
        Matcher word = Pattern.compile("([a-z-]+(?:\\s[a-z-]+)?)\\s*mile").matcher(text.toLowerCase());
        if (word.find()) {
            Integer n = wordsToNumber(word.group(1).trim());
            if (n != null) {
                return n;
            }
        }
        return -1;
    }

    private static Integer findNumberWord(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, Integer> e : NUMBER_WORDS.entrySet()) {
            if (lower.matches(".*\\b" + e.getKey() + "\\b.*")) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Integer wordsToNumber(String phrase) {
        String[] parts = phrase.replace("-", " ").split("\\s+");
        int total = 0;
        boolean matched = false;
        for (String p : parts) {
            Integer v = NUMBER_WORDS.get(p);
            if (v != null) {
                total += v;
                matched = true;
            }
        }
        return matched ? total : null;
    }

    /** Very small time-of-day extractor covering "18:30", "8pm", "half seven" style phrases. */
    static String findTime(String text) {
        String lower = text.toLowerCase();

        Matcher iso = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").matcher(lower);
        if (iso.find()) {
            return String.format("%02d:%02d", Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)));
        }

        Matcher half = Pattern.compile("half\\s*(?:past\\s*)?([a-z]+|\\d{1,2})").matcher(lower);
        if (half.find()) {
            Integer hour = parseHourToken(half.group(1));
            if (hour != null) {
                hour = to24Hour(hour, lower);
                return String.format("%02d:30", hour);
            }
        }

        Matcher ampm = Pattern.compile("\\b(\\d{1,2})\\s*(am|pm)\\b").matcher(lower);
        if (ampm.find()) {
            int hour = Integer.parseInt(ampm.group(1));
            boolean pm = ampm.group(2).equals("pm");
            if (pm && hour != 12) {
                hour += 12;
            }
            if (!pm && hour == 12) {
                hour = 0;
            }
            return String.format("%02d:00", hour);
        }

        return null;
    }

    private static Integer parseHourToken(String token) {
        if (token.matches("\\d{1,2}")) {
            return Integer.parseInt(token);
        }
        return NUMBER_WORDS.get(token);
    }

    private static int to24Hour(int hour, String context) {
        // Evening collection/delivery context is the common case in this domain.
        if (hour >= 1 && hour <= 7 && !context.contains("am")) {
            return hour + 12;
        }
        return hour;
    }

    static boolean mentionsNegativeCollection(String text) {
        String lower = text.toLowerCase();
        return lower.contains("cannot collect") || lower.contains("can't collect") || lower.contains("no transport")
                || lower.contains("nobody who can collect") || lower.contains("no one to collect")
                || lower.contains("unable to collect");
    }
}
