package com.foodflow.agent;

import com.foodflow.model.Donation;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/** Represents the supermarket's side of the negotiation: quantity, food type and deadline. */
@Component
public class SupermarketAgent {

    public String describe(Donation donation) {
        String dietary = donation.getDietaryTypes() == null || donation.getDietaryTypes().isEmpty()
                ? "" : String.join(", ", donation.getDietaryTypes()) + " ";
        return donation.getQuantity() + " " + dietary + "meals are available"
                + (donation.getExpiresAt() != null ? " until " + donation.getExpiresAt() : "") + ".";
    }

    /** Simulates the Supermarket Agent confirming a later pickup time is still safe. */
    public String confirmPickupTime(Donation donation, LocalTime pickupTime) {
        if (pickupTime == null || donation.getAvailableFrom() == null || !pickupTime.isAfter(donation.getAvailableFrom())) {
            return donation.getSupermarketName() + " confirms the meals are ready for collection.";
        }
        return donation.getSupermarketName() + " confirms the meals can be safely stored until " + pickupTime + ".";
    }
}
