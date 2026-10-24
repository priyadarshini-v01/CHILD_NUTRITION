package com.childnutrition.dto;

import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() { }
    public record Day(LocalDate date, MealDtos.Totals nutrition) { }
    public record Period(LocalDate start, LocalDate end, List<Day> days) { }
    public record Dashboard(ChildDtos.Response child, MealDtos.Totals dailyNutrition,
                           GrowthDtos.Summary growth, List<String> recommendations,
                           List<AlertDtos.Response> alerts) { }
}
