package com.foodflow.web;

import com.foodflow.model.CharityRequest;
import com.foodflow.service.IntakeService;
import com.foodflow.store.CharityRequestStore;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/charity-requests")
public class CharityRequestController {

    private final CharityRequestStore charityRequestStore;
    private final IntakeService intakeService;

    public CharityRequestController(CharityRequestStore charityRequestStore, IntakeService intakeService) {
        this.charityRequestStore = charityRequestStore;
        this.intakeService = intakeService;
    }

    @PostMapping
    public CharityRequest create(@RequestBody CharityRequest charityRequest) {
        return intakeService.intake(charityRequest);
    }

    @GetMapping
    public List<CharityRequest> list() {
        return charityRequestStore.findAll().stream()
                .sorted(Comparator.comparing(CharityRequest::getCreatedAt).reversed())
                .toList();
    }
}
