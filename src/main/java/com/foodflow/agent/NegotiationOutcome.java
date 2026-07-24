package com.foodflow.agent;

import com.foodflow.model.RescuePlan;

public class NegotiationOutcome {
    public final boolean hadCandidate;
    public final RescuePlan plan;

    public NegotiationOutcome(boolean hadCandidate, RescuePlan plan) {
        this.hadCandidate = hadCandidate;
        this.plan = plan;
    }
}
