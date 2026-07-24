package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyCheckResult {
    private String name;
    private boolean passed;
    private String detail;
}
