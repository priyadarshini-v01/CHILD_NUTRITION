package com.childnutrition.controller;

import com.childnutrition.dto.NutritionAssistantDtos;
import com.childnutrition.service.ChildAccessService;
import com.childnutrition.service.NutritionAssistantService;
import com.childnutrition.service.NutritionAssistantRateLimiter;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nutrition-assistant")
public class NutritionAssistantController {
    private final NutritionAssistantService assistant;
    private final ChildAccessService access;
    private final NutritionAssistantRateLimiter rateLimiter;
    public NutritionAssistantController(NutritionAssistantService assistant, ChildAccessService access, NutritionAssistantRateLimiter rateLimiter) { this.assistant=assistant; this.access=access; this.rateLimiter=rateLimiter; }

    @PostMapping("/ask")
    public NutritionAssistantDtos.Response ask(@Valid @RequestBody NutritionAssistantDtos.Request request, Authentication auth) {
        rateLimiter.check(auth.getName());
        var child = request.childId() == null ? null : access.require(request.childId(), auth);
        if (request.history() != null && request.history().stream().anyMatch(t -> !"user".equals(t.role()) && !"assistant".equals(t.role())))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Conversation history contains an invalid role.");
        return assistant.answer(request.question(), auth.getName(), child, request.history(), request.reportDate());
    }
}
