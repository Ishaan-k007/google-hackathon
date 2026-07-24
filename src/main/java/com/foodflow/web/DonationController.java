package com.foodflow.web;

import com.foodflow.model.Donation;
import com.foodflow.service.IntakeService;
import com.foodflow.store.DonationStore;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationStore donationStore;
    private final IntakeService intakeService;

    public DonationController(DonationStore donationStore, IntakeService intakeService) {
        this.donationStore = donationStore;
        this.intakeService = intakeService;
    }

    @PostMapping
    public Donation create(@RequestBody Donation donation) {
        return intakeService.intake(donation);
    }

    @GetMapping
    public List<Donation> list() {
        return donationStore.findAll().stream()
                .sorted(Comparator.comparing(Donation::getCreatedAt).reversed())
                .toList();
    }
}
