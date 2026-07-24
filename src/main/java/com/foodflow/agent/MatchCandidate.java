package com.foodflow.agent;

import com.foodflow.model.CharityRequest;
import com.foodflow.model.Donation;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.SafetyCheckResult;
import com.foodflow.model.ScoreBreakdown;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Internal computation artifact: one candidate donation/charity/driver combination under evaluation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchCandidate {

    private Donation donation;
    private CharityRequest charity;
    private DriverAvailability driver; // null when the charity self-collects

    private int allocatedMeals;
    private LocalTime pickupTime;
    private LocalTime deliveryTime;
    private double distanceMiles;

    @Builder.Default
    private List<SafetyCheckResult> safetyChecks = new ArrayList<>();

    private boolean safetyPassed;

    @Builder.Default
    private List<String> rejectionReasons = new ArrayList<>();

    private ScoreBreakdown scoreBreakdown;
    private int scorePercent;

    public long failedCheckCount() {
        return safetyChecks.stream().filter(c -> !c.isPassed()).count();
    }
}
