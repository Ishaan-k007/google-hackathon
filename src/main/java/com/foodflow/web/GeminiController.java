package com.foodflow.web;

import com.foodflow.service.GeminiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public record InterpretRequest(String role, String text) {
    }

    @PostMapping("/interpret")
    public Map<String, Object> interpret(@RequestBody InterpretRequest request) {
        return geminiService.interpret(request.role(), request.text());
    }
}
