package com.childnutrition.controller;

import com.childnutrition.dto.FoodDtos;
import com.childnutrition.service.FoodNutritionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/foods")
public class FoodNutritionController {
    private final FoodNutritionService foods;
    public FoodNutritionController(FoodNutritionService foods) { this.foods = foods; }

    @GetMapping
    public List<FoodDtos.Response> all(@RequestParam(required = false) String category,
                                       @RequestParam(required = false) String query, Authentication auth) {
        return query == null ? foods.all(category, auth.getName()) : foods.search(query, category, auth.getName());
    }

    @GetMapping("/search")
    public List<FoodDtos.Response> search(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(required = false) String category, Authentication auth) {
        return foods.search(q, category, auth.getName());
    }

    @GetMapping("/categories")
    public List<String> categories() { return foods.categories(); }

    @GetMapping("/{id}")
    public FoodDtos.Response get(@PathVariable Long id, Authentication auth) { return foods.get(id, auth.getName()); }

    @GetMapping("/{id}/portion")
    public FoodDtos.PortionResponse portion(@PathVariable Long id, @RequestParam java.math.BigDecimal amount, Authentication auth) {
        return foods.portion(id, amount, auth.getName());
    }

    @PostMapping("/custom") @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public FoodDtos.Response createCustom(@Valid @RequestBody FoodDtos.CustomRequest request, Authentication auth) {
        return foods.createCustom(request, auth.getName());
    }
}
