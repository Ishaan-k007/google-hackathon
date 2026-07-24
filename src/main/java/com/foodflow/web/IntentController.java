package com.foodflow.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodflow.service.GeminiService;
import com.foodflow.service.IntentResult;
import com.foodflow.store.IntentStore;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures free-text submissions from each portal, turns them into structured intent
 * JSON via Gemini (with a single clarification round-trip if something important is
 * missing), and stores the finished records for an external agentic workflow to consume
 * via the GET endpoints below. This app's own negotiation demo does not read from here.
 */
@RestController
@RequestMapping("/api/intents")
public class IntentController {

    private final GeminiService geminiService;
    private final IntentStore intentStore;

    public IntentController(GeminiService geminiService, IntentStore intentStore) {
        this.geminiService = geminiService;
        this.intentStore = intentStore;
    }

    public record IntentRequest(String text, @JsonProperty("final") Boolean isFinal) {
        boolean finalOrDefault() {
            return isFinal != null && isFinal;
        }
    }

    public record IntentResponse(boolean complete, String clarificationQuestion, Map<String, Object> data) {
    }

    @PostMapping("/donor")
    public IntentResponse donor(@RequestBody IntentRequest request) {
        IntentResult result = geminiService.interpretIntent("supermarket", request.text(), request.finalOrDefault());
        return respond(result, intentStore::addDonor);
    }

    @PostMapping("/charity")
    public IntentResponse charity(@RequestBody IntentRequest request) {
        IntentResult result = geminiService.interpretIntent("charity", request.text(), request.finalOrDefault());
        return respond(result, intentStore::addCharity);
    }

    @PostMapping("/driver")
    public IntentResponse driver(@RequestBody IntentRequest request) {
        IntentResult result = geminiService.interpretIntent("driver", request.text(), request.finalOrDefault());
        return respond(result, intentStore::addDriver);
    }

    @GetMapping("/donors")
    public List<Map<String, Object>> donors() {
        return intentStore.findDonors();
    }

    @GetMapping("/charities")
    public List<Map<String, Object>> charities() {
        return intentStore.findCharities();
    }

    @GetMapping("/drivers")
    public List<Map<String, Object>> drivers() {
        return intentStore.findDrivers();
    }

    @GetMapping
    public Map<String, Object> all() {
        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("donors", intentStore.findDonors());
        combined.put("charities", intentStore.findCharities());
        combined.put("drivers", intentStore.findDrivers());
        return combined;
    }

    private IntentResponse respond(IntentResult result, java.util.function.Function<Map<String, Object>, Map<String, Object>> store) {
        if (result.isComplete()) {
            Map<String, Object> stored = store.apply(result.getData() != null ? result.getData() : Map.of());
            return new IntentResponse(true, null, stored);
        }
        return new IntentResponse(false, result.getClarificationQuestion(), result.getData());
    }
}
