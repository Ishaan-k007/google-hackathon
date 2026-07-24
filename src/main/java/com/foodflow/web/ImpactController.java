package com.foodflow.web;

import com.foodflow.model.ImpactStats;
import com.foodflow.model.RescuePlan;
import com.foodflow.store.RescuePlanStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Estimates - explicitly labelled as such downstream - of the impact of confirmed
 * rescues. One meal supports approximately one person; one meal represents
 * approximately 0.6 kg of food, per the FoodFlow product spec.
 */
@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    private static final double KG_PER_MEAL = 0.6;

    private final RescuePlanStore rescuePlanStore;

    public ImpactController(RescuePlanStore rescuePlanStore) {
        this.rescuePlanStore = rescuePlanStore;
    }

    @GetMapping
    public ImpactStats impact() {
        List<RescuePlan> confirmed = rescuePlanStore.findConfirmed();
        int mealsRescued = confirmed.stream().mapToInt(RescuePlan::getAllocatedMeals).sum();
        double distance = confirmed.stream().mapToDouble(RescuePlan::getEstimatedDistanceMiles).sum();

        return ImpactStats.builder()
                .mealsRescued(mealsRescued)
                .foodWastePreventedKg(Math.round(mealsRescued * KG_PER_MEAL * 10.0) / 10.0)
                .peopleSupported(mealsRescued)
                .distanceMilesTravelled(Math.round(distance * 10.0) / 10.0)
                .confirmedRescueCount(confirmed.size())
                .build();
    }
}
