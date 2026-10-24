package com.childnutrition.controller;

import com.childnutrition.dto.MealDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.Meal;
import com.childnutrition.entity.FoodNutrition;
import com.childnutrition.repository.FoodNutritionRepository;
import com.childnutrition.repository.MealRepository;
import com.childnutrition.service.ChildAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/children/{childId}")
public class MealController {
    private final MealRepository meals; private final ChildAccessService access; private final FoodNutritionRepository foods;
    public MealController(MealRepository meals, ChildAccessService access, FoodNutritionRepository foods) { this.meals = meals; this.access = access; this.foods = foods; }

    @PostMapping("/meals") @ResponseStatus(HttpStatus.CREATED)
    public MealDtos.Response create(@PathVariable Long childId, @Valid @RequestBody MealDtos.Request request, Authentication auth) {
        Child child = access.require(childId, auth); Meal meal = new Meal(); meal.setChild(child); apply(meal, request, auth.getName());
        return MealDtos.Response.from(meals.save(meal));
    }
    @GetMapping("/meals")
    public List<MealDtos.Response> list(@PathVariable Long childId, @RequestParam(required=false) LocalDate date, Authentication auth) {
        Child child = access.require(childId, auth);
        return (date == null ? meals.findByChildId(child.getId()) : meals.findByChildIdAndMealDate(child.getId(), date))
                .stream().map(MealDtos.Response::from).toList();
    }
    @PutMapping("/meals/{mealId}")
    public MealDtos.Response update(@PathVariable Long childId, @PathVariable Long mealId,
            @Valid @RequestBody MealDtos.Request request, Authentication auth) {
        Child child = access.require(childId, auth); Meal meal = requireMeal(child, mealId); apply(meal, request, auth.getName());
        return MealDtos.Response.from(meals.save(meal));
    }
    @DeleteMapping("/meals/{mealId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long childId, @PathVariable Long mealId, Authentication auth) {
        Child child = access.require(childId, auth); meals.delete(requireMeal(child, mealId));
    }
    @GetMapping("/nutrition/daily")
    public MealDtos.Totals daily(@PathVariable Long childId, @RequestParam(required=false) LocalDate date, Authentication auth) {
        Child child = access.require(childId, auth); LocalDate day = date == null ? LocalDate.now() : date;
        return totals(meals.findByChildIdAndMealDate(child.getId(), day));
    }
    public static MealDtos.Totals totals(List<Meal> rows) {
        return new MealDtos.Totals(sumKnown(rows, Meal::getCalories), sumKnown(rows, Meal::getProtein),
                sumKnown(rows, Meal::getCarbohydrates), sumKnown(rows, Meal::getFat), collect(rows, Meal::getVitamins),
                collect(rows, Meal::getMinerals), rows.size(), sumKnown(rows, Meal::getSugar), sumKnown(rows, Meal::getFiber), sumKnown(rows, Meal::getSodium), sumKnown(rows, Meal::getSaturatedFat));
    }
    private static List<String> collect(List<Meal> rows, java.util.function.Function<Meal, String> get) {
        return rows.stream().map(get).filter(java.util.Objects::nonNull).flatMap(s -> java.util.Arrays.stream(s.split(",")))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
    private static BigDecimal sumKnown(List<Meal> rows, java.util.function.Function<Meal, BigDecimal> get) {
        if (rows.isEmpty()) return BigDecimal.ZERO;
        if (rows.stream().anyMatch(row -> get.apply(row) == null)) return null;
        return rows.stream().map(get).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private Meal requireMeal(Child child, Long id) {
        return meals.findByIdAndChildId(id, child.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
    }
    private void apply(Meal meal, MealDtos.Request r, String owner) {
        meal.setMealDate(r.mealDate()); meal.setMealTime(r.mealTime()); meal.setMealType(r.mealType());
        FoodNutrition food = foods.findVisibleById(r.foodId(), owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food was not found"));
        meal.setFood(food); meal.setFoodName(food.getFoodName()); meal.setQuantity(r.quantity());
        meal.setQuantityUnit(food.getServingUnit());
        BigDecimal factor = r.quantity().divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
        meal.setCalories(scale(food.getCalories(), factor)); meal.setProtein(scale(food.getProtein(), factor));
        meal.setCarbohydrates(scale(food.getCarbohydrates(), factor)); meal.setFat(scale(food.getFat(), factor));
        meal.setSugar(scale(food.getSugar(), factor)); meal.setFiber(scale(food.getFiber(), factor));
        meal.setSodium(scale(food.getSodium(), factor)); meal.setSaturatedFat(scale(food.getSaturatedFat(), factor));
        meal.setVitamins(r.vitamins()); meal.setMinerals(r.minerals()); meal.setNotes(r.notes());
    }
    private BigDecimal scale(BigDecimal value, BigDecimal factor) { return value == null ? null : value.multiply(factor); }
}
