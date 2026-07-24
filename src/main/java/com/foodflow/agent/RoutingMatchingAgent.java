package com.foodflow.agent;

import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.SafetyCheckResult;
import com.foodflow.model.ScoreBreakdown;
import com.foodflow.service.DistanceService;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds compatible supermarket/charity/driver combinations, simulates distance and
 * timing, delegates the safety verdict to {@link SafetyAgent}, and ranks valid
 * matches with a transparent, explainable score (not a machine-learning prediction).
 */
@Component
public class RoutingMatchingAgent {

    private static final double AVERAGE_SPEED_MPH = 20.0;

    private final SafetyAgent safetyAgent;
    private final DistanceService distanceService;

    public RoutingMatchingAgent(SafetyAgent safetyAgent, DistanceService distanceService) {
        this.safetyAgent = safetyAgent;
        this.distanceService = distanceService;
    }

    public static class MatchResult {
        public final List<MatchCandidate> allCandidates;
        public final MatchCandidate best;

        public MatchResult(List<MatchCandidate> allCandidates, MatchCandidate best) {
            this.allCandidates = allCandidates;
            this.best = best;
        }

        public boolean hasCandidate() {
            return best != null;
        }
    }

    public MatchResult findBestMatch(List<Donation> donations, List<CharityRequest> charities,
                                      List<DriverAvailability> drivers) {
        List<MatchCandidate> candidates = new ArrayList<>();

        for (Donation donation : donations) {
            for (CharityRequest charity : charities) {
                if (charity.isCanCollect()) {
                    candidates.add(buildCandidate(donation, charity, null));
                }
                for (DriverAvailability driver : drivers) {
                    candidates.add(buildCandidate(donation, charity, driver));
                }
            }
        }

        if (candidates.isEmpty()) {
            return new MatchResult(candidates, null);
        }

        MatchCandidate best = candidates.stream()
                .filter(MatchCandidate::isSafetyPassed)
                .max(Comparator.comparingInt(MatchCandidate::getScorePercent))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingLong(MatchCandidate::failedCheckCount))
                        .orElse(candidates.get(0)));

        return new MatchResult(candidates, best);
    }

    public MatchCandidate buildCandidate(Donation donation, CharityRequest charity, DriverAvailability driver) {
        int allocatedMeals = Math.max(0, Math.min(donation.getQuantity(), charity.getRequestedQuantity()));

        double distanceMiles = driver == null ? 0.0
                : distanceService.estimateMiles(donation.getPickupLocation(), charity.getDeliveryLocation());

        LocalTime pickupTime = computePickupTime(donation, driver);
        LocalTime deliveryTime = computeDeliveryTime(pickupTime, driver, distanceMiles);

        List<SafetyCheckResult> checks = safetyAgent.evaluate(donation, charity, driver, allocatedMeals,
                pickupTime, deliveryTime, distanceMiles);
        boolean passed = checks.stream().allMatch(SafetyCheckResult::isPassed);
        List<String> reasons = checks.stream().filter(c -> !c.isPassed()).map(SafetyCheckResult::getDetail).toList();

        MatchCandidate candidate = MatchCandidate.builder()
                .donation(donation)
                .charity(charity)
                .driver(driver)
                .allocatedMeals(allocatedMeals)
                .pickupTime(pickupTime)
                .deliveryTime(deliveryTime)
                .distanceMiles(distanceMiles)
                .safetyChecks(checks)
                .safetyPassed(passed)
                .rejectionReasons(new ArrayList<>(reasons))
                .build();

        if (passed) {
            ScoreBreakdown breakdown = score(candidate);
            candidate.setScoreBreakdown(breakdown);
            candidate.setScorePercent(breakdown.getTotal());
        }

        return candidate;
    }

    private LocalTime computePickupTime(Donation donation, DriverAvailability driver) {
        if (donation.getAvailableFrom() == null) {
            return null;
        }
        if (driver == null) {
            return donation.getAvailableFrom();
        }
        if (driver.getAvailableFrom() == null) {
            return null;
        }
        return donation.getAvailableFrom().isAfter(driver.getAvailableFrom())
                ? donation.getAvailableFrom() : driver.getAvailableFrom();
    }

    private LocalTime computeDeliveryTime(LocalTime pickupTime, DriverAvailability driver, double distanceMiles) {
        if (pickupTime == null) {
            return null;
        }
        if (driver == null) {
            return pickupTime;
        }
        long travelMinutes = Math.round(distanceMiles / AVERAGE_SPEED_MPH * 60.0 / 5.0) * 5;
        travelMinutes = Math.max(travelMinutes, 5);
        return pickupTime.plusMinutes(travelMinutes);
    }

    private ScoreBreakdown score(MatchCandidate c) {
        Donation d = c.getDonation();
        CharityRequest ch = c.getCharity();
        DriverAvailability driver = c.getDriver();

        int dietaryMax = 30, allergenMax = 25, timingMax = 20, quantityMax = 10, capacityMax = 5, distanceMax = 10;

        Set<String> overlap = new HashSet<>(lower(d.getDietaryTypes()));
        overlap.retainAll(lower(ch.getAcceptedDietaryTypes()));
        int dietaryPoints = d.getDietaryTypes().isEmpty() ? 0
                : clamp(Math.round(dietaryMax * (overlap.size() / (float) d.getDietaryTypes().size())), 0, dietaryMax);

        int allergenPoints = allergenMax;

        int timingPoints = timingMax;
        if (c.getDeliveryTime() != null) {
            LocalTime deadline = ch.getLatestDeliveryTime() != null
                    && (d.getExpiresAt() == null || ch.getLatestDeliveryTime().isBefore(d.getExpiresAt()))
                    ? ch.getLatestDeliveryTime() : d.getExpiresAt();
            if (deadline != null) {
                long bufferMinutes = java.time.Duration.between(c.getDeliveryTime(), deadline).toMinutes();
                timingPoints = clamp(Math.round(timingMax * (bufferMinutes / 60f)), 0, timingMax);
            }
        }

        int quantityPoints = ch.getRequestedQuantity() <= 0 ? 0
                : clamp(Math.round(quantityMax * (c.getAllocatedMeals() / (float) ch.getRequestedQuantity())), 0, quantityMax);

        int capacityPoints = capacityMax;

        int distancePoints = distanceMax;
        if (driver != null && driver.getMaximumDistanceMiles() > 0) {
            distancePoints = clamp(Math.round((float) (distanceMax * (1 - c.getDistanceMiles() / driver.getMaximumDistanceMiles()))), 0, distanceMax);
        }

        int total = dietaryPoints + allergenPoints + timingPoints + quantityPoints + capacityPoints + distancePoints;

        return ScoreBreakdown.builder()
                .dietaryCompatibility(dietaryPoints).dietaryCompatibilityMax(dietaryMax)
                .allergenCompatibility(allergenPoints).allergenCompatibilityMax(allergenMax)
                .collectionTiming(timingPoints).collectionTimingMax(timingMax)
                .quantityFulfilled(quantityPoints).quantityFulfilledMax(quantityMax)
                .driverCapacity(capacityPoints).driverCapacityMax(capacityMax)
                .travelDistance(distancePoints).travelDistanceMax(distanceMax)
                .total(clamp(total, 0, 100))
                .build();
    }

    private List<String> lower(List<String> list) {
        return list == null ? List.of() : list.stream().map(String::toLowerCase).toList();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
