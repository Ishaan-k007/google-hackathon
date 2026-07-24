package com.foodflow.store;

import com.foodflow.model.FoodbankAlert;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Holds foodbank alerts generated from external match-result JSON, newest first. */
@Component
public class AlertStore {

    private final List<FoodbankAlert> alerts = new ArrayList<>();
    private final Object lock = new Object();

    public FoodbankAlert add(FoodbankAlert alert) {
        synchronized (lock) {
            alerts.add(alert);
        }
        return alert;
    }

    public List<FoodbankAlert> findAll() {
        synchronized (lock) {
            List<FoodbankAlert> copy = new ArrayList<>(alerts);
            copy.sort(Comparator.comparing(FoodbankAlert::getCreatedAt).reversed());
            return copy;
        }
    }

    public void clear() {
        synchronized (lock) {
            alerts.clear();
        }
    }
}
