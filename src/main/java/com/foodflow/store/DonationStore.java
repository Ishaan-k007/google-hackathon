package com.foodflow.store;

import com.foodflow.model.Donation;
import com.foodflow.model.DonationStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DonationStore extends InMemoryStore<Donation> {

    @Override
    protected String idOf(Donation item) {
        return item.getId();
    }

    public List<Donation> findAvailable() {
        return findAll().stream()
                .filter(d -> d.getStatus() == DonationStatus.AVAILABLE)
                .toList();
    }
}
