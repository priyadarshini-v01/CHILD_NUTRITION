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
        if (q.isBlank()) return all(category, owner);
        List<FoodNutrition> rows = category == null || category.isBlank()
                ? foods.searchVisible(owner, q, org.springframework.data.domain.PageRequest.of(0, 25))
                : foods.searchVisibleByCategory(owner, q, category.trim(), org.springframework.data.domain.PageRequest.of(0, 25));
        return rows.stream().map(FoodDtos.Response::from).toList();
    }

    public FoodDtos.Response get(Long id, String owner) {
        return foods.findVisibleById(id, owner).map(FoodDtos.Response::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
    }

    @Transactional
    public FoodDtos.Response createCustom(FoodDtos.CustomRequest request, String owner) {
        if (foods.existsByFoodNameIgnoreCaseAndCustomOwnerEmail(request.foodName().trim(), owner))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a custom food with this name");
        if (request.calories() == null && request.protein() == null && request.carbohydrates() == null && request.fat() == null
                && request.saturatedFat() == null && request.sugar() == null && request.fiber() == null && request.sodium() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter at least one nutrition value");
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

    public List<String> categories() { return foods.findActiveCategories(); }
}
