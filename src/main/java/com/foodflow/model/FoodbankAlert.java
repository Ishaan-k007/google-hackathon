package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/** A human-readable notification generated from a match-result JSON, addressed to a foodbank/charity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodbankAlert {
    private String id;
    private String foodbankName;
    private String message;
    private Map<String, Object> sourceJson;
    private Instant createdAt;
}
