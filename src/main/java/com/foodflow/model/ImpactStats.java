package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactStats {
    private int mealsRescued;
    private double foodWastePreventedKg;
    private int peopleSupported;
    private double distanceMilesTravelled;
    private int confirmedRescueCount;
}
