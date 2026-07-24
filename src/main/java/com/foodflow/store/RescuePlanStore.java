package com.foodflow.store;

import com.foodflow.model.PlanStatus;
import com.foodflow.model.RescuePlan;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class RescuePlanStore extends InMemoryStore<RescuePlan> {

    @Override
    protected String idOf(RescuePlan item) {
        return item.getId();
    }

    public Optional<RescuePlan> findLatest() {
        return findAll().stream()
                .max(Comparator.comparing(RescuePlan::getCreatedAt));
    }

    public List<RescuePlan> findConfirmed() {
        return findAll().stream()
                .filter(p -> p.getStatus() == PlanStatus.CONFIRMED)
                .toList();
    }
}
