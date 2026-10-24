package com.childnutrition.dto;

import com.childnutrition.entity.NutritionTarget;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class NutritionTargetDtos {
    private NutritionTargetDtos() { }
    public record Request(@NotBlank String ageGroup, @NotNull @DecimalMin("0.0") BigDecimal calorieTarget,
            @NotNull @DecimalMin("0.0") BigDecimal proteinTarget, @NotNull @DecimalMin("0.0") BigDecimal carbohydrateTarget,
            @NotNull @DecimalMin("0.0") BigDecimal fatTarget) { }
    public record Response(Long id, String ageGroup, BigDecimal calorieTarget, BigDecimal proteinTarget,
            BigDecimal carbohydrateTarget, BigDecimal fatTarget) {
        public static Response from(NutritionTarget t) { return new Response(t.getId(), t.getAgeGroup(), t.getCalorieTarget(),
                t.getProteinTarget(), t.getCarbohydrateTarget(), t.getFatTarget()); }
    }
}
