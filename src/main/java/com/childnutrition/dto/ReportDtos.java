package com.childnutrition.dto;

import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() { }
    public record Day(LocalDate date, MealDtos.Totals nutrition, List<NutrientReport> nutrients,
                      List<MealGroup> mealBreakdown, boolean incomplete) { }
    public record Period(LocalDate start, LocalDate end, List<Day> days) { }
    public record Reference(java.math.BigDecimal amount, String name, String ageRange, String unit, String version, String url) { }
    public record FoodExample(String name, String dataQuality, String sourceName) { }
    public record NutrientReport(String name, java.math.BigDecimal actual, String unit, Reference reference,
                                 java.math.BigDecimal percentReached, java.math.BigDecimal difference,
                                 boolean complete, boolean possibleGap, String explanation,
                                 List<FoodExample> foodsThatContainNutrient) { }
    public record MealGroup(String mealType, int mealCount, MealDtos.Totals nutrition) { }
    public record DailyNutrition(LocalDate date, Integer childAgeYears, String childSex,
                                 List<NutrientReport> nutrients, List<MealGroup> mealBreakdown,
                                 boolean incomplete, List<String> incompletenessReasons,
                                 String whatThisMeans) { }
    public record Dashboard(ChildDtos.Response child, MealDtos.Totals dailyNutrition,
                           GrowthDtos.Summary growth, List<String> recommendations,
                           List<AlertDtos.Response> alerts) { }
}
