package com.foodflow.agent;

import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.SafetyCheckResult;
import com.foodflow.model.StorageType;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The ONLY component allowed to decide whether a match is safe. Every rule here is
 * explicit and deterministic - Gemini is never consulted for these decisions.
 */
@Component
public class SafetyAgent {

    public List<SafetyCheckResult> evaluate(Donation donation, CharityRequest charity, DriverAvailability driver,
                                             int allocatedMeals, LocalTime pickupTime, LocalTime deliveryTime,
                                             double distanceMiles) {
        List<SafetyCheckResult> checks = new ArrayList<>();

        checks.add(checkDietaryCompatibility(donation, charity));
        checks.add(checkAllergenCompatibility(donation, charity));
        checks.add(checkEssentialInfoConfirmed(donation));
        checks.add(checkInsulatedTransport(donation, driver));
        checks.add(checkExpiry(donation, pickupTime, deliveryTime));
        checks.add(checkCharityDeadline(charity, deliveryTime));
        checks.add(checkAllocation(allocatedMeals));

        if (driver != null) {
            checks.add(checkDriverCapacity(driver, allocatedMeals));
            checks.add(checkDriverDistance(driver, distanceMiles));
            checks.add(checkDriverWindow(driver, pickupTime));
        }

        return checks;
    }

    private SafetyCheckResult checkDietaryCompatibility(Donation donation, CharityRequest charity) {
        List<String> donationTypes = donation.getDietaryTypes();
        List<String> acceptedTypes = charity.getAcceptedDietaryTypes();
        if (donationTypes == null || donationTypes.isEmpty() || acceptedTypes == null || acceptedTypes.isEmpty()) {
            return SafetyCheckResult.builder().name("Dietary compatibility").passed(false)
                    .detail("Essential dietary information has not been confirmed.").build();
        }
        Set<String> overlap = intersect(donationTypes, acceptedTypes);
        if (overlap.isEmpty()) {
            return SafetyCheckResult.builder().name("Dietary compatibility").passed(false)
                    .detail("The dietary category is incompatible with what the charity can accept.").build();
        }
        return SafetyCheckResult.builder().name("Dietary compatibility").passed(true)
                .detail("The dietary categories are compatible.").build();
    }

    private SafetyCheckResult checkAllergenCompatibility(Donation donation, CharityRequest charity) {
        Set<String> conflicts = intersect(donation.getAllergens(), charity.getRejectedAllergens());
        if (!conflicts.isEmpty()) {
            return SafetyCheckResult.builder().name("Allergen compatibility").passed(false)
                    .detail("The charity cannot accept food containing " + String.join(" or ", conflicts) + ".").build();
        }
        return SafetyCheckResult.builder().name("Allergen compatibility").passed(true)
                .detail("No confirmed allergen conflict has been found.").build();
    }

    private SafetyCheckResult checkEssentialInfoConfirmed(Donation donation) {
        if (donation.getStorageType() == null) {
            return SafetyCheckResult.builder().name("Essential information").passed(false)
                    .detail("Essential safety information has not been confirmed (storage type is unknown).").build();
        }
        return SafetyCheckResult.builder().name("Essential information").passed(true)
                .detail("All essential safety fields have been confirmed.").build();
    }

    private SafetyCheckResult checkInsulatedTransport(Donation donation, DriverAvailability driver) {
        boolean needsInsulation = donation.getStorageType() == StorageType.CHILLED
                || donation.getStorageType() == StorageType.FROZEN;
        if (!needsInsulation || driver == null) {
            return SafetyCheckResult.builder().name("Chilled/frozen transport").passed(true)
                    .detail(driver == null ? "No driver transport leg is required." : "No special transport storage is required.").build();
        }
        if (!driver.isHasInsulatedStorage()) {
            return SafetyCheckResult.builder().name("Chilled/frozen transport").passed(false)
                    .detail("Chilled transport is required, but the driver does not have insulated storage.").build();
        }
        return SafetyCheckResult.builder().name("Chilled/frozen transport").passed(true)
                .detail("Insulated transport is available for the donation's storage requirement.").build();
    }

    private SafetyCheckResult checkExpiry(Donation donation, LocalTime pickupTime, LocalTime deliveryTime) {
        if (pickupTime == null) {
            return SafetyCheckResult.builder().name("Expiry timing").passed(false)
                    .detail("A pickup time within the available window could not be found.").build();
        }
        if (donation.getExpiresAt() != null && pickupTime.isAfter(donation.getExpiresAt())) {
            return SafetyCheckResult.builder().name("Expiry timing").passed(false)
                    .detail("The proposed collection time is after the food's expiry deadline.").build();
        }
        if (donation.getExpiresAt() != null && deliveryTime != null && deliveryTime.isAfter(donation.getExpiresAt())) {
            return SafetyCheckResult.builder().name("Expiry timing").passed(false)
                    .detail("The proposed delivery time is after the food's expiry deadline.").build();
        }
        return SafetyCheckResult.builder().name("Expiry timing").passed(true)
                .detail("Collection is scheduled within the food's expiry window.").build();
    }

    private SafetyCheckResult checkCharityDeadline(CharityRequest charity, LocalTime deliveryTime) {
        if (charity.getLatestDeliveryTime() == null || deliveryTime == null) {
            return SafetyCheckResult.builder().name("Charity delivery deadline").passed(true)
                    .detail("No delivery deadline was specified.").build();
        }
        if (deliveryTime.isAfter(charity.getLatestDeliveryTime())) {
            return SafetyCheckResult.builder().name("Charity delivery deadline").passed(false)
                    .detail("The estimated delivery time is after the charity's latest acceptable delivery time.").build();
        }
        return SafetyCheckResult.builder().name("Charity delivery deadline").passed(true)
                .detail("Delivery is expected before the charity's deadline.").build();
    }

    private SafetyCheckResult checkAllocation(int allocatedMeals) {
        if (allocatedMeals <= 0) {
            return SafetyCheckResult.builder().name("Meal allocation").passed(false)
                    .detail("There are not enough meals available to fulfil any part of this request.").build();
        }
        return SafetyCheckResult.builder().name("Meal allocation").passed(true)
                .detail(allocatedMeals + " meals can be allocated.").build();
    }

    private SafetyCheckResult checkDriverCapacity(DriverAvailability driver, int allocatedMeals) {
        if (driver.getCapacityMeals() < allocatedMeals) {
            return SafetyCheckResult.builder().name("Driver capacity").passed(false)
                    .detail("The driver cannot carry the required quantity.").build();
        }
        return SafetyCheckResult.builder().name("Driver capacity").passed(true)
                .detail("The driver has sufficient capacity for the allocated meals.").build();
    }

    private SafetyCheckResult checkDriverDistance(DriverAvailability driver, double distanceMiles) {
        if (distanceMiles > driver.getMaximumDistanceMiles()) {
            return SafetyCheckResult.builder().name("Travel distance").passed(false)
                    .detail("The total route exceeds the driver's maximum travel distance.").build();
        }
        return SafetyCheckResult.builder().name("Travel distance").passed(true)
                .detail("The proposed route is within the driver's travel limit.").build();
    }

    private SafetyCheckResult checkDriverWindow(DriverAvailability driver, LocalTime pickupTime) {
        if (pickupTime == null || driver.getAvailableFrom() == null) {
            return SafetyCheckResult.builder().name("Driver availability window").passed(false)
                    .detail("The driver's availability window has not been confirmed.").build();
        }
        boolean afterStart = !pickupTime.isBefore(driver.getAvailableFrom());
        boolean beforeEnd = driver.getAvailableUntil() == null || !pickupTime.isAfter(driver.getAvailableUntil());
        if (!afterStart || !beforeEnd) {
            return SafetyCheckResult.builder().name("Driver availability window").passed(false)
                    .detail("The driver is unavailable during the required pickup window.").build();
        }
        return SafetyCheckResult.builder().name("Driver availability window").passed(true)
                .detail("The driver is available during the required pickup window.").build();
    }

    private Set<String> intersect(List<String> a, List<String> b) {
        if (a == null || b == null) {
            return Set.of();
        }
        Set<String> setA = new HashSet<>();
        for (String s : a) {
            setA.add(s.toLowerCase());
        }
        Set<String> result = new HashSet<>();
        for (String s : b) {
            if (setA.contains(s.toLowerCase())) {
                result.add(s.toLowerCase());
            }
        }
        return result;
    }
}
