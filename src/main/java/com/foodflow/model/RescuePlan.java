package com.foodflow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescuePlan {

    private String id;

    private String donationId;
    private String charityRequestId;
    private String driverAvailabilityId;

    // Snapshot display fields so the frontend doesn't need extra joins
    private String supermarketName;
    private String charityName;
    private String driverName;
    private String foodDescription;
    private List<String> dietaryTypes;

    private int allocatedMeals;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime pickupTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime estimatedDeliveryTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime foodDeadline;

    private double estimatedDistanceMiles;

    private int matchScorePercent;
    private ScoreBreakdown scoreBreakdown;

    private boolean safetyPassed;

    @Builder.Default
    private List<SafetyCheckResult> safetyChecks = new ArrayList<>();

    @Builder.Default
    private List<String> rejectionReasons = new ArrayList<>();

    private String explanation;

    @Builder.Default
    private PlanStatus status = PlanStatus.PROPOSED;

    private Instant createdAt;
    private Instant confirmedAt;
}
