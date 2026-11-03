package com.childnutrition.service;

import com.childnutrition.dto.FoodDtos;
import com.childnutrition.entity.FoodNutrition;
import com.childnutrition.repository.FoodNutritionRepository;
import com.childnutrition.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;

@Service
@Transactional(readOnly = true)
public class FoodNutritionService {
    private final FoodNutritionRepository foods;
    private final UserRepository users;
    public FoodNutritionService(FoodNutritionRepository foods, UserRepository users) { this.foods = foods; this.users = users; }

    public List<FoodDtos.Response> all(String category, String owner) {
        List<FoodNutrition> rows = foods.findVisibleByCategory(owner, category == null || category.isBlank() ? null : category.trim());
        return rows.stream().map(FoodDtos.Response::from).toList();
    }

    public List<FoodDtos.Response> search(String query, String category, String owner) {
        String q = query == null ? "" : query.trim();
        if (q.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food search must be 100 characters or fewer.");
        if (category != null && category.length() > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid food category.");
        if (q.isBlank()) return all(category, owner);
        List<FoodNutrition> rows = category == null || category.isBlank()
                ? foods.searchVisible(owner, q, org.springframework.data.domain.PageRequest.of(0, 25))
                : foods.searchVisibleByCategory(owner, q, category.trim(), org.springframework.data.domain.PageRequest.of(0, 25));
        return rows.stream().map(FoodDtos.Response::from).toList();
    }

    public FoodDtos.Response get(Long id, String owner) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food ID must be a positive number.");
        return foods.findVisibleById(id, owner).map(FoodDtos.Response::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
    }

    public FoodDtos.PortionResponse portion(Long id, BigDecimal amount, String owner) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("10000")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serving size must be greater than zero and no more than 10000 g or ml.");
        FoodDtos.Response food = get(id, owner);
        BigDecimal factor = amount.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        var values = new LinkedHashMap<String, BigDecimal>();
        values.put("caloriesKcal", scale(food.calories(), factor)); values.put("proteinG", scale(food.protein(), factor));
        values.put("carbohydratesG", scale(food.carbohydrates(), factor)); values.put("fatG", scale(food.fat(), factor));
        values.put("fiberG", scale(food.fiber(), factor)); values.put("sugarG", scale(food.sugar(), factor));
        values.put("sodiumMg", scale(food.sodium(), factor)); values.put("calciumMg", scale(food.calcium(), factor));
        values.put("ironMg", scale(food.iron(), factor)); values.put("vitaminCMg", scale(food.vitaminC(), factor)); values.put("folate", scale(food.folate(), factor));
        String unit = food.nutritionBasis() != null && food.nutritionBasis().toLowerCase().contains("ml") ? "ml" : "g";
        return new FoodDtos.PortionResponse(food, amount, unit, values);
    }

    @Transactional
    public FoodDtos.Response createCustom(FoodDtos.CustomRequest request, String owner) {
        String foodName = request.foodName().trim();
        if (foods.existsByFoodNameIgnoreCaseAndCustomOwnerEmail(foodName, owner))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a custom food with this name");
        if (request.calories() == null && request.protein() == null && request.carbohydrates() == null && request.fat() == null
                && request.saturatedFat() == null && request.sugar() == null && request.fiber() == null && request.sodium() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter at least one nutrition value");
        BigDecimal knownMacros = nz(request.protein()).add(nz(request.carbohydrates())).add(nz(request.fat()));
        if ("g".equals(request.servingUnit()) && knownMacros.compareTo(request.servingSize().add(new BigDecimal("0.1"))) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Protein, carbohydrates and fat cannot exceed the label serving weight combined.");
        if (request.saturatedFat() != null && request.fat() != null && request.saturatedFat().compareTo(request.fat()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saturated fat cannot be greater than total fat.");
        if (request.sugar() != null && request.carbohydrates() != null && request.sugar().compareTo(request.carbohydrates()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sugar cannot be greater than carbohydrates.");
        var user = users.findByEmail(owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        BigDecimal scale = new BigDecimal("100").divide(request.servingSize(), 8, RoundingMode.HALF_UP);
        FoodNutrition food = new FoodNutrition(); food.setFoodName(request.foodName().trim());
        food.setCalories(scale(request.calories(), scale)); food.setProtein(scale(request.protein(), scale));
        food.setCarbohydrates(scale(request.carbohydrates(), scale)); food.setFat(scale(request.fat(), scale));
        food.setSaturatedFat(scale(request.saturatedFat(), scale)); food.setSugar(scale(request.sugar(), scale));
        food.setFiber(scale(request.fiber(), scale)); food.setSodium(scale(request.sodium(), scale));
        food.setServingSize(request.servingSize()); food.setServingUnit(request.servingUnit());
        food.setServingBasis("per " + request.servingSize().stripTrailingZeros().toPlainString() + " " + request.servingUnit());
        food.setNutritionBasis("per 100 " + request.servingUnit()); food.setCategory("Custom foods"); food.setDefaultMealType("Snack");
        food.setSourceName("User provided"); food.setSourceReference(request.sourceReference()); food.setDataQuality("USER_PROVIDED");
        food.setSourceRetrievedAt(LocalDate.now());
        food.setCustomOwner(user); food.setActive(true);
        return FoodDtos.Response.from(foods.save(food));
    }

    private BigDecimal scale(BigDecimal value, BigDecimal factor) {
        if (value == null) return null;
        BigDecimal normalized = value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        if (normalized.precision() - normalized.scale() > 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serving values are too large to normalize");
        return normalized;
    }

    private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    public List<String> categories() { return foods.findActiveCategories(); }
}
