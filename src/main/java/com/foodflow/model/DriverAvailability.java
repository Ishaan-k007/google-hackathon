package com.foodflow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAvailability {

    private String id;
    private String driverName;
    private String startingLocation;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime availableFrom;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime availableUntil;

    private double maximumDistanceMiles;
    private int capacityMeals;
    private boolean hasInsulatedStorage;
    private String additionalNotes;

    @Builder.Default
    private DriverStatus status = DriverStatus.AVAILABLE;

    private Instant createdAt;
}
