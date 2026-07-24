package com.foodflow.web;

import com.foodflow.agent.CoordinatorAgent;
import com.foodflow.agent.NegotiationOutcome;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

    private final CoordinatorAgent coordinatorAgent;

    public MatchingController(CoordinatorAgent coordinatorAgent) {
        this.coordinatorAgent = coordinatorAgent;
    }

    @PostMapping("/run")
    public Map<String, Object> run() {
        NegotiationOutcome outcome = coordinatorAgent.runNegotiation();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hadCandidate", outcome.hadCandidate);
        response.put("plan", outcome.plan);
        return response;
    }
}
