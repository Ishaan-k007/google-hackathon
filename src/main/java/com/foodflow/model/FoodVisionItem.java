package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** One food item Gemini Vision identified in a donation photo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodVisionItem {
    private String name;
    private String category;
    private Integer estimatedQuantity;

    @Builder.Default
    private List<String> dietaryTypes = new ArrayList<>();

    @Builder.Default
    private List<String> likelyAllergens = new ArrayList<>();

    private Double confidence;
}
