package com.childnutrition.dto;

import com.childnutrition.entity.NutritionTarget;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class NutritionTargetDtos {
    private NutritionTargetDtos() { }
    public record Request(@NotBlank @Size(max=40) @Pattern(regexp="0-1|1-3|4-8|9-13|14-18") String ageGroup,
            @NotNull @DecimalMin("0.0") @Digits(integer=8, fraction=2) BigDecimal calorieTarget,
            @NotNull @DecimalMin("0.0") @Digits(integer=8, fraction=2) BigDecimal proteinTarget,
            @NotNull @DecimalMin("0.0") @Digits(integer=8, fraction=2) BigDecimal carbohydrateTarget,
            @NotNull @DecimalMin("0.0") @Digits(integer=8, fraction=2) BigDecimal fatTarget) { }
    public record Response(Long id, String ageGroup, BigDecimal calorieTarget, BigDecimal proteinTarget,
            BigDecimal carbohydrateTarget, BigDecimal fatTarget) {
        public static Response from(NutritionTarget t) { return new Response(t.getId(), t.getAgeGroup(), t.getCalorieTarget(),
                t.getProteinTarget(), t.getCarbohydrateTarget(), t.getFatTarget()); }
    }
}
