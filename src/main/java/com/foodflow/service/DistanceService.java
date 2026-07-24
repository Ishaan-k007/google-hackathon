package com.foodflow.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Simulates road distance between two free-text locations. Real geocoding/routing
 * is explicitly out of scope for the MVP, so this gives deterministic, plausible
 * mileage: known demo locations resolve to fixed values (so the flagship scenario
 * reads naturally), anything else gets a stable hash-based estimate so repeated
 * runs with the same input always agree.
 */
@Service
public class DistanceService {

    private static final Map<String, Double> KNOWN_PAIRS = Map.of(
            key("Green Market, 12 High Street", "Hope Community Kitchen, 45 Elm Road"), 4.2,
            key("Sunrise Bakery, 8 Mill Lane", "Comfort Food Shelter, 20 Bridge Street"), 3.1
    );

    public double estimateMiles(String pickupLocation, String deliveryLocation) {
        String pickup = normalize(pickupLocation);
        String delivery = normalize(deliveryLocation);
        Double known = KNOWN_PAIRS.get(pickup + "|" + delivery);
        if (known != null) {
            return known;
        }
        int hash = Math.abs((pickup + "|" + delivery).hashCode());
        double miles = 1.0 + (hash % 85) / 10.0; // 1.0 - 9.4 miles, stable per input pair
        return Math.round(miles * 10.0) / 10.0;
    }

    private static String key(String pickup, String delivery) {
        return normalize(pickup) + "|" + normalize(delivery);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
