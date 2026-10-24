package com.childnutrition.dto;

import com.childnutrition.entity.GrowthRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class GrowthDtos {
    private GrowthDtos() { }
    public record Request(@NotNull LocalDate recordDate, @NotNull @DecimalMin(value="0.0", inclusive=false) BigDecimal heightCm,
                         @NotNull @DecimalMin(value="0.0", inclusive=false) BigDecimal weightKg, String notes) { }
    public record Response(Long id, LocalDate recordDate, BigDecimal heightCm, BigDecimal weightKg, BigDecimal bmi,
                           String bmiCategory, String notes) {
        public static Response from(GrowthRecord r) { return new Response(r.getId(), r.getRecordDate(), r.getHeightCm(),
                r.getWeightKg(), r.getBmi(), r.getBmiCategory(), r.getNotes()); }
    }
    public record Summary(Response latest, BigDecimal heightChangeCm, BigDecimal weightChangeKg) { }
}
