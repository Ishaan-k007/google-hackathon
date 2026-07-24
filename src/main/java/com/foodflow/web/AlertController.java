package com.foodflow.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodflow.model.FoodbankAlert;
import com.foodflow.service.AlertGeneration;
import com.foodflow.service.GeminiService;
import com.foodflow.store.AlertStore;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Receiving end for the external agentic workflow's match results. It is NOT this app's job
 * to decide matches here - just to turn whatever JSON that workflow produces into a readable
 * alert for the named foodbank/charity, and make it visible (currently: the Charity portal
 * polls {@link #list}). Accepts any JSON shape - no fixed schema is assumed.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final GeminiService geminiService;
    private final AlertStore alertStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlertController(GeminiService geminiService, AlertStore alertStore) {
        this.geminiService = geminiService;
        this.alertStore = alertStore;
    }

    @PostMapping("/foodbank")
    public FoodbankAlert postAlert(@RequestBody JsonNode matchResultJson) {
        AlertGeneration generated = geminiService.generateFoodbankAlert(matchResultJson);
        Map<String, Object> sourceJson = objectMapper.convertValue(matchResultJson, LinkedHashMap.class);

        FoodbankAlert alert = FoodbankAlert.builder()
                .id(UUID.randomUUID().toString())
                .foodbankName(generated.getFoodbankName())
                .message(generated.getMessage())
                .sourceJson(sourceJson)
                .createdAt(Instant.now())
                .build();

        return alertStore.add(alert);
    }

    @GetMapping("/foodbank")
    public List<FoodbankAlert> list() {
        return alertStore.findAll();
    }
}
