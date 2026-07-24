package com.foodflow.store;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the structured intent JSON captured from each portal's free-text submission,
 * ready to be picked up by an external agentic workflow (not consumed by anything in
 * this app - FoodFlow's own negotiation demo still runs off the separate Donation /
 * CharityRequest / DriverAvailability stores via the dashboard's demo-data loaders).
 */
@Component
public class IntentStore {

    private final List<Map<String, Object>> donors = new ArrayList<>();
    private final List<Map<String, Object>> charities = new ArrayList<>();
    private final List<Map<String, Object>> drivers = new ArrayList<>();
    private final Object lock = new Object();

    private final AtomicInteger donorSeq = new AtomicInteger(1);
    private final AtomicInteger charitySeq = new AtomicInteger(1);
    private final AtomicInteger driverSeq = new AtomicInteger(1);

    public Map<String, Object> addDonor(Map<String, Object> data) {
        return add(donors, data, "donor_id", "SUPER-", donorSeq);
    }

    public Map<String, Object> addCharity(Map<String, Object> data) {
        return add(charities, data, "charity_id", "CHAR-", charitySeq);
    }

    public Map<String, Object> addDriver(Map<String, Object> data) {
        return add(drivers, data, "driver_id", "DRV-", driverSeq);
    }

    public List<Map<String, Object>> findDonors() {
        synchronized (lock) {
            return new ArrayList<>(donors);
        }
    }

    public List<Map<String, Object>> findCharities() {
        synchronized (lock) {
            return new ArrayList<>(charities);
        }
    }

    public List<Map<String, Object>> findDrivers() {
        synchronized (lock) {
            return new ArrayList<>(drivers);
        }
    }

    public void clear() {
        synchronized (lock) {
            donors.clear();
            charities.clear();
            drivers.clear();
            donorSeq.set(1);
            charitySeq.set(1);
            driverSeq.set(1);
        }
    }

    private Map<String, Object> add(List<Map<String, Object>> list, Map<String, Object> data, String idField,
                                     String idPrefix, AtomicInteger seq) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(idField, idPrefix + String.format("%03d", seq.getAndIncrement()));
        record.putAll(data);
        record.put("captured_at", Instant.now().toString());
        synchronized (lock) {
            list.add(record);
        }
        return record;
    }
}
