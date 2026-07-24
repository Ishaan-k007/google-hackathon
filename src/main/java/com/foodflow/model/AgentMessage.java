package com.foodflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {
    private String id;
    private AgentType agentType;
    private String message;
    private Severity severity;
    private Instant timestamp;

    /** Milliseconds after negotiation start at which this message should become visible to pollers. */
    private long revealOffsetMs;
}
