package com.foodflow.service;

import com.foodflow.model.CharityRequest;
import com.foodflow.model.CharityStatus;
import com.foodflow.model.Donation;
import com.foodflow.model.DonationStatus;
import com.foodflow.model.DriverAvailability;
import com.foodflow.model.DriverStatus;
import com.foodflow.store.CharityRequestStore;
import com.foodflow.store.DonationStore;
import com.foodflow.store.DriverStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Normalizes and persists a freshly-submitted donation, charity request or driver availability. */
@Service
public class IntakeService {

    private final DonationStore donationStore;
    private final CharityRequestStore charityRequestStore;
    private final DriverStore driverStore;
    private final GeminiService geminiService;

    public IntakeService(DonationStore donationStore, CharityRequestStore charityRequestStore,
                          DriverStore driverStore, GeminiService geminiService) {
        this.donationStore = donationStore;
        this.charityRequestStore = charityRequestStore;
        this.driverStore = driverStore;
        this.geminiService = geminiService;
    }

    public Donation intake(Donation donation) {
        donation.setId(UUID.randomUUID().toString());
        donation.setCreatedAt(Instant.now());
        donation.setStatus(DonationStatus.AVAILABLE);
        donation.setClarificationQuestion(geminiService.detectDonationClarification(donation));
        return donationStore.save(donation);
    }

    public CharityRequest intake(CharityRequest charityRequest) {
        charityRequest.setId(UUID.randomUUID().toString());
        charityRequest.setCreatedAt(Instant.now());
        charityRequest.setStatus(CharityStatus.PENDING);
        return charityRequestStore.save(charityRequest);
    }

    public DriverAvailability intake(DriverAvailability driver) {
        driver.setId(UUID.randomUUID().toString());
        driver.setCreatedAt(Instant.now());
        driver.setStatus(DriverStatus.AVAILABLE);
        return driverStore.save(driver);
    }
}
