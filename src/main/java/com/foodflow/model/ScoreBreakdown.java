package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBreakdown {
    private int dietaryCompatibility;
    private int dietaryCompatibilityMax;
    private int allergenCompatibility;
    private int allergenCompatibilityMax;
    private int collectionTiming;
    private int collectionTimingMax;
    private int quantityFulfilled;
    private int quantityFulfilledMax;
    private int driverCapacity;
    private int driverCapacityMax;
    private int travelDistance;
    private int travelDistanceMax;
    private int total;
}
