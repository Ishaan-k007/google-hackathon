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
public class CharityRequest {

    private String id;
    private String charityName;
    private String deliveryLocation;
    private int requestedQuantity;

    @Builder.Default
    private List<String> acceptedDietaryTypes = new ArrayList<>();

    /** Hard constraint - allergens this charity can never accept. */
    @Builder.Default
    private List<String> rejectedAllergens = new ArrayList<>();

    /** Soft preferences extracted from free text, informational only. */
    @Builder.Default
    private List<String> preferences = new ArrayList<>();

    @JsonFormat(pattern = "HH:mm")
    private LocalTime latestDeliveryTime;

    private String storageCapabilities;
    private boolean canCollect;
    private String additionalNotes;

    @Builder.Default
    private CharityStatus status = CharityStatus.PENDING;

    private Instant createdAt;
}
