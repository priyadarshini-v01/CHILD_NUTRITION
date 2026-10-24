package com.childnutrition.controller;

import com.childnutrition.dto.AlertDtos;
import com.childnutrition.dto.ChildDtos;
import com.childnutrition.dto.GrowthDtos;
import com.childnutrition.dto.MealDtos;
import com.childnutrition.dto.ReportDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.Alert;
import com.childnutrition.repository.AlertRepository;
import com.childnutrition.entity.GrowthRecord;
import com.childnutrition.entity.Meal;
import com.childnutrition.repository.GrowthRecordRepository;
import com.childnutrition.repository.MealRepository;
import com.childnutrition.service.ChildAccessService;
import com.childnutrition.service.InsightsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/children/{childId}")
public class InsightsController {
    private final ChildAccessService access; private final InsightsService insights; private final AlertRepository alerts;
    private final MealRepository meals; private final GrowthRecordRepository growth;
    public InsightsController(ChildAccessService access, InsightsService insights, MealRepository meals,
                              GrowthRecordRepository growth, AlertRepository alerts) {
        this.access=access; this.insights=insights; this.meals=meals; this.growth=growth; this.alerts=alerts;
    }
    @GetMapping("/recommendations")
    public List<String> recommendations(@PathVariable Long childId, @RequestParam(required=false) LocalDate date, Authentication auth) {
        Child child=access.require(childId, auth); return insights.recommendations(child, date==null?LocalDate.now():date);
    }
    @GetMapping("/alerts")
    public List<AlertDtos.Response> alerts(@PathVariable Long childId, @RequestParam(required=false) LocalDate date, Authentication auth) {
        Child child=access.require(childId, auth); return insights.alerts(child, date==null?LocalDate.now():date);
    }
    @PatchMapping("/alerts/{alertId}/read")
    public AlertDtos.Response markRead(@PathVariable Long childId, @PathVariable Long alertId, Authentication auth) {
        Child child=access.require(childId, auth);
        Alert alert=alerts.findByIdAndChildId(alertId, child.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alert not found"));
        alert.setRead(true); return AlertDtos.Response.from(alerts.save(alert));
    }
    @GetMapping("/dashboard")
    public ReportDtos.Dashboard dashboard(@PathVariable Long childId, @RequestParam(required=false) LocalDate date, Authentication auth) {
        Child child=access.require(childId, auth); LocalDate day=date==null?LocalDate.now():date;
        List<GrowthRecord> history=growth.findByChildIdOrderByRecordDateDesc(childId);
        GrowthDtos.Summary summary;
        if (history.isEmpty()) summary=new GrowthDtos.Summary(null, BigDecimal.ZERO, BigDecimal.ZERO);
        else { GrowthRecord latest=history.get(0), earliest=history.get(history.size()-1);
            summary=new GrowthDtos.Summary(GrowthDtos.Response.from(latest), latest.getHeightCm().subtract(earliest.getHeightCm()), latest.getWeightKg().subtract(earliest.getWeightKg())); }
        MealDtos.Totals daily=MealController.totals(meals.findByChildIdAndMealDate(childId, day));
        return new ReportDtos.Dashboard(ChildDtos.Response.from(child), daily, summary,
                insights.recommendations(child, day), insights.alerts(child, day));
    }
    @GetMapping("/reports/weekly")
    public ReportDtos.Period weekly(@PathVariable Long childId, @RequestParam(required=false) LocalDate date, Authentication auth) {
        LocalDate end=date==null?LocalDate.now():date; return report(access.require(childId, auth), end.minusDays(6), end);
    }
    @GetMapping("/reports/monthly")
    public ReportDtos.Period monthly(@PathVariable Long childId, @RequestParam(required=false) String month, Authentication auth) {
        access.require(childId, auth); YearMonth ym=month==null?YearMonth.now():YearMonth.parse(month);
        return report(access.require(childId, auth), ym.atDay(1), ym.atEndOfMonth());
    }
    private ReportDtos.Period report(Child child, LocalDate start, LocalDate end) {
        List<Meal> rows=meals.findByChildIdAndMealDateBetweenOrderByMealDateAscMealTimeAsc(child.getId(), start, end);
        List<ReportDtos.Day> days=new ArrayList<>();
        for(LocalDate day=start; !day.isAfter(end); day=day.plusDays(1)) {
            LocalDate current=day;
            List<Meal> daily=rows.stream().filter(m -> m.getMealDate().equals(current)).toList();
            days.add(new ReportDtos.Day(current, MealController.totals(daily)));
        }
        return new ReportDtos.Period(start, end, days);
    }
}
