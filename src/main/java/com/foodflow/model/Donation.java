package com.foodflow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Donation {

    private String id;
    private String supermarketName;
    private String pickupLocation;
    private String foodDescription;
    private int quantity;

    @Builder.Default
    private List<String> dietaryTypes = new ArrayList<>();

    @Builder.Default
    private List<String> allergens = new ArrayList<>();

    private StorageType storageType;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime availableFrom;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime expiresAt;

    private String additionalNotes;

    @Builder.Default
    private DonationStatus status = DonationStatus.AVAILABLE;

    /** Gemini-detected missing safety detail, e.g. asking about unmentioned allergens. Narrative only - does not block matching. */
    private String clarificationQuestion;

    private Instant createdAt;
}
