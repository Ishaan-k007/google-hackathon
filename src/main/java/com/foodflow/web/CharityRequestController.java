package com.foodflow.web;

import com.foodflow.model.CharityRequest;
import com.foodflow.service.IntakeService;
import com.foodflow.database.CharityRequestDatabase;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/charity-requests")
public class CharityRequestController {

    private final CharityRequestDatabase charityRequestDatabase;
    private final IntakeService intakeService;

    public CharityRequestController(CharityRequestDatabase charityRequestDatabase, IntakeService intakeService) {
        this.charityRequestDatabase = charityRequestDatabase;
        this.intakeService = intakeService;
    }

    @PostMapping
    public CharityRequest create(@RequestBody CharityRequest charityRequest) {
        return intakeService.intake(charityRequest);
    }

    @GetMapping
    public List<CharityRequest> list() {
        return charityRequestDatabase.findAll().stream()
                .sorted(Comparator.comparing(CharityRequest::getCreatedAt).reversed())
                .toList();
    }
}
