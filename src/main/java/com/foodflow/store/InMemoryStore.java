package com.foodflow.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal thread-safe in-memory repository shared by all FoodFlow entity stores.
 * A hackathon MVP has no database - everything lives in process memory and is
 * wiped by the "Reset demo" control.
 */
public abstract class InMemoryStore<T> {

    private final Map<String, T> records = new LinkedHashMap<>();
    private final Object lock = new Object();

    protected abstract String idOf(T item);

    public T save(T item) {
        synchronized (lock) {
            records.put(idOf(item), item);
        }
        return item;
    }

    public Optional<T> findById(String id) {
        synchronized (lock) {
            return Optional.ofNullable(records.get(id));
        }
    }

    public List<T> findAll() {
        synchronized (lock) {
            return new ArrayList<>(records.values());
        }
    }

    public void clear() {
        synchronized (lock) {
            records.clear();
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return records.isEmpty();
        }
    }
}
