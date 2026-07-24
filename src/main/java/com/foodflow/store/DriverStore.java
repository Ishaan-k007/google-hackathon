package com.foodflow.store;

import com.foodflow.model.DriverAvailability;
import com.foodflow.model.DriverStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DriverStore extends InMemoryStore<DriverAvailability> {

    @Override
    protected String idOf(DriverAvailability item) {
        return item.getId();
    }

    public List<DriverAvailability> findAvailable() {
        return findAll().stream()
                .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
                .toList();
    }
}
