package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Result of asking Gemini Vision to identify the food in a donation photo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodVisionResult {
    private boolean success;

    @Builder.Default
    private List<FoodVisionItem> items = new ArrayList<>();

    /** Plain-English sentence describing the photo, phrased so it can seed the donor's free-text form. */
    private String summary;

    private String error;
}
