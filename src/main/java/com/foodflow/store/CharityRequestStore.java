package com.foodflow.store;

import com.foodflow.model.CharityRequest;
import com.foodflow.model.CharityStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CharityRequestStore extends InMemoryStore<CharityRequest> {

    @Override
    protected String idOf(CharityRequest item) {
        return item.getId();
    }

    public List<CharityRequest> findPending() {
        return findAll().stream()
                .filter(c -> c.getStatus() == CharityStatus.PENDING)
                .toList();
    }
}
