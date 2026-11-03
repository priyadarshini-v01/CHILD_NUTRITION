package com.childnutrition.dto;

import com.childnutrition.entity.GrowthRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

public final class GrowthDtos {
    private GrowthDtos() { }
    public record Request(@NotNull @PastOrPresent LocalDate recordDate,
                          @NotNull @DecimalMin(value="0.0", inclusive=false) @Digits(integer=3, fraction=2) BigDecimal heightCm,
                          @NotNull @DecimalMin(value="0.0", inclusive=false) @Digits(integer=3, fraction=2) BigDecimal weightKg,
                          @Size(max=2000) String notes,
                          @Size(max=2048) String confirmationToken) { }
    public record Measurement(Long id, LocalDate recordDate, BigDecimal heightCm, BigDecimal weightKg) { }
    public record SaveChallenge(String code, String message, String confirmationToken,
                                Measurement previous, Measurement entered) { }
    public record Response(Long id, LocalDate recordDate, BigDecimal heightCm, BigDecimal weightKg, BigDecimal bmi,
                           String bmiCategory, String notes) {
        public static Response from(GrowthRecord r) { return new Response(r.getId(), r.getRecordDate(), r.getHeightCm(),
                r.getWeightKg(), r.getBmi(), category(r), r.getNotes()); }
        private static String category(GrowthRecord r) {
            if (r.getBmi() == null || r.getChild() == null || r.getChild().getDateOfBirth() == null) return null;
            int age = Period.between(r.getChild().getDateOfBirth(), r.getRecordDate()).getYears();
            if (age < 18) return "BMI-for-age reference required";
            return r.getBmi().compareTo(new BigDecimal("18.5")) < 0 ? "Below the standard range"
                    : r.getBmi().compareTo(new BigDecimal("25")) < 0 ? "Within the standard range"
                    : r.getBmi().compareTo(new BigDecimal("30")) < 0 ? "Above the standard range" : "High BMI range";
        }
    }
    public record Summary(Response latest, BigDecimal heightChangeCm, BigDecimal weightChangeKg) { }
}
