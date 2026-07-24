package com.foodflow.web;

import com.foodflow.model.FoodVisionResult;
import com.foodflow.service.GeminiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets a donor snap or upload a photo of surplus food and have Gemini Vision identify it. */
@RestController
@RequestMapping("/api/vision")
public class VisionController {

    private final GeminiService geminiService;

    public VisionController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public record AnalyzeRequest(String imageBase64, String mimeType) {
    }

    @PostMapping("/analyze-food")
    public FoodVisionResult analyzeFood(@RequestBody AnalyzeRequest request) {
        return geminiService.analyzeFoodImage(request.imageBase64(), request.mimeType());
    }
}
