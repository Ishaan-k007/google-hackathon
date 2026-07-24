package com.foodflow.service;

import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.StorageType;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/** Canonical demo entities: the flagship "good" rescue scenario and a deliberately unsafe one. */
@Service
public class DemoDataService {

    public Donation goodDonation() {
        return Donation.builder()
                .supermarketName("Green Market")
                .pickupLocation("Green Market, 12 High Street")
                .foodDescription("Vegetarian pasta meals")
                .quantity(30)
                .dietaryTypes(List.of("vegetarian"))
                .allergens(List.of("dairy"))
                .storageType(StorageType.CHILLED)
                .availableFrom(LocalTime.of(18, 0))
                .expiresAt(LocalTime.of(20, 0))
                .additionalNotes("Meals contain dairy. Must be collected before 20:00.")
                .build();
    }

    public CharityRequest goodCharityRequest() {
        return CharityRequest.builder()
                .charityName("Hope Community Kitchen")
                .deliveryLocation("Hope Community Kitchen, 45 Elm Road")
                .requestedQuantity(20)
                .acceptedDietaryTypes(List.of("vegetarian"))
                .rejectedAllergens(List.of("nuts"))
                .latestDeliveryTime(LocalTime.of(19, 30))
                .storageCapabilities("Fridge available on site")
                .canCollect(false)
                .additionalNotes("Cannot accept nuts. Nobody available to collect.")
                .build();
    }

    public DriverAvailability goodDriver() {
        return DriverAvailability.builder()
                .driverName("Alex")
                .startingLocation("Riverside Lane")
                .availableFrom(LocalTime.of(18, 30))
                .availableUntil(LocalTime.of(20, 0))
                .maximumDistanceMiles(6)
                .capacityMeals(25)
                .hasInsulatedStorage(true)
                .additionalNotes("Can travel up to six miles.")
                .build();
    }

    public Donation unsafeDonation() {
        return Donation.builder()
                .supermarketName("Sunrise Bakery")
                .pickupLocation("Sunrise Bakery, 8 Mill Lane")
                .foodDescription("Mixed pastries and energy bars")
                .quantity(25)
                .dietaryTypes(List.of("vegetarian"))
                .allergens(List.of("nuts"))
                .storageType(StorageType.AMBIENT)
                .availableFrom(LocalTime.of(17, 30))
                .expiresAt(LocalTime.of(19, 30))
                .additionalNotes("Contains a variety of nuts.")
                .build();
    }

    public CharityRequest unsafeCharityRequest() {
        return CharityRequest.builder()
                .charityName("Comfort Food Shelter")
                .deliveryLocation("Comfort Food Shelter, 20 Bridge Street")
                .requestedQuantity(15)
                .acceptedDietaryTypes(List.of("vegetarian"))
                .rejectedAllergens(List.of("nuts"))
                .latestDeliveryTime(LocalTime.of(19, 0))
                .storageCapabilities("Dry storage")
                .canCollect(false)
                .additionalNotes("Several residents have severe nut allergies.")
                .build();
    }

    public DriverAvailability unsafeDriver() {
        return DriverAvailability.builder()
                .driverName("Priya")
                .startingLocation("Bridge Street Depot")
                .availableFrom(LocalTime.of(17, 45))
                .availableUntil(LocalTime.of(19, 30))
                .maximumDistanceMiles(5)
                .capacityMeals(20)
                .hasInsulatedStorage(false)
                .additionalNotes("")
                .build();
    }
}
