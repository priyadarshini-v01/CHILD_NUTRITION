package com.childnutrition.dto;

import com.childnutrition.entity.FoodNutrition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class FoodDtos {
    private FoodDtos() { }
    public record Response(Long id, String foodName, String category, String defaultMealType,
                           BigDecimal servingSize, String servingUnit, String servingBasis, String sourceName,
                           BigDecimal calories, BigDecimal protein, BigDecimal carbohydrates, BigDecimal fat,
                           BigDecimal fiber, BigDecimal sugar, BigDecimal sodium, BigDecimal calcium,
                           BigDecimal iron, BigDecimal vitaminC, BigDecimal folate, BigDecimal saturatedFat, String nutritionBasis,
                           String dataQuality, String sourceReference, LocalDate sourceRetrievedAt, boolean custom) {
        public static Response from(FoodNutrition food) {
            return new Response(food.getId(), food.getFoodName(), food.getCategory(), food.getDefaultMealType(),
                    food.getServingSize(), food.getServingUnit(), food.getServingBasis(), food.getSourceName(),
                    food.getCalories(), food.getProtein(), food.getCarbohydrates(), food.getFat(), food.getFiber(),
                    food.getSugar(), food.getSodium(), food.getCalcium(), food.getIron(), food.getVitaminC(), food.getFolate(), food.getSaturatedFat(), food.getNutritionBasis(),
                    food.getDataQuality(), food.getSourceReference(), food.getSourceRetrievedAt(), food.getCustomOwner() != null);
        }
    }
    public record PortionResponse(Response food, BigDecimal amount, String unit, Map<String, BigDecimal> nutrients) { }

    public record CustomRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=180) String foodName,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin(value="0.1") @jakarta.validation.constraints.DecimalMax("10000") @jakarta.validation.constraints.Digits(integer=5, fraction=2) BigDecimal servingSize,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="g|ml") String servingUnit,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal calories,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal protein,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal carbohydrates,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal fat,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal saturatedFat,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal sugar,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal fiber,
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100000") @jakarta.validation.constraints.Digits(integer=6, fraction=2) BigDecimal sodium,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=500) String sourceReference) { }
}
