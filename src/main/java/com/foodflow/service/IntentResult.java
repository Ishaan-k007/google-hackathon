package com.foodflow.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Result of interpreting one free-text submission into a structured intent JSON blob. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    private boolean complete;
    private String clarificationQuestion;
    private Map<String, Object> data;
    private String source; // "gemini" or "fallback"
}
