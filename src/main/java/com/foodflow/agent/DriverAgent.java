package com.foodflow.agent;

import com.foodflow.model.DriverAvailability;
import org.springframework.stereotype.Component;

/** Represents the volunteer driver's side of the negotiation: availability, capacity and range. */
@Component
public class DriverAgent {

    public String describe(DriverAvailability driver) {
        return driver.getDriverName() + " is available from " + driver.getAvailableFrom() + "."
                + "\nCapacity: " + driver.getCapacityMeals() + " meals."
                + "\nMaximum distance: " + formatMiles(driver.getMaximumDistanceMiles()) + " miles.";
    }

    public String confirmCollectionTime(DriverAvailability driver, java.time.LocalTime pickupTime) {
        return driver.getDriverName() + " confirms collection at " + pickupTime + ".";
    }

    private String formatMiles(double miles) {
        return miles == Math.floor(miles) ? String.valueOf((int) miles) : String.valueOf(miles);
    }
}
