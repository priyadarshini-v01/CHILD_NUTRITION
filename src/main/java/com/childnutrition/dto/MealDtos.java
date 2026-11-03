package com.childnutrition.dto;

import com.childnutrition.entity.Meal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class MealDtos {
    private MealDtos() { }
    public record Request(@NotNull @PastOrPresent LocalDate mealDate, LocalTime mealTime,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="(?i:Breakfast|Lunch|Dinner|Snack)") String mealType,
            @NotNull @Positive Long foodId, @NotNull @Positive @jakarta.validation.constraints.DecimalMax("10000") @Digits(integer=5, fraction=2) BigDecimal quantity,
            @Size(max=2000) String vitamins, @Size(max=2000) String minerals, @Size(max=2000) String notes) { }
    public record Response(Long id, LocalDate mealDate, LocalTime mealTime, String mealType, Long foodId, String foodName,
            BigDecimal quantity, String quantityUnit, BigDecimal calories, BigDecimal protein, BigDecimal carbohydrates, BigDecimal fat,
            BigDecimal sugar, BigDecimal fiber, BigDecimal sodium, BigDecimal saturatedFat, String vitamins, String minerals, String notes, LocalDateTime createdAt) {
        public static Response from(Meal m) { return new Response(m.getId(), m.getMealDate(), m.getMealTime(), m.getMealType(),
                m.getFood() == null ? null : m.getFood().getId(), m.getFoodName(), m.getQuantity(), m.getQuantityUnit(), m.getCalories(), m.getProtein(), m.getCarbohydrates(), m.getFat(),
                m.getSugar(), m.getFiber(), m.getSodium(), m.getSaturatedFat(), m.getVitamins(), m.getMinerals(), m.getNotes(), m.getCreatedAt()); }
    }
    public record Totals(BigDecimal calories, BigDecimal protein, BigDecimal carbohydrates, BigDecimal fat,
                         List<String> vitamins, List<String> minerals, int mealCount, BigDecimal sugar, BigDecimal fiber, BigDecimal sodium, BigDecimal saturatedFat) { }
}

