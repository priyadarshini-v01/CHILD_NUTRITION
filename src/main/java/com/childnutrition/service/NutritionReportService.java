package com.childnutrition.service;

import com.childnutrition.controller.MealController;
import com.childnutrition.dto.MealDtos;
import com.childnutrition.dto.ReportDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.FoodNutrition;
import com.childnutrition.entity.Meal;
import com.childnutrition.repository.FoodNutritionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class NutritionReportService {
    private static final String RDA_URL = "https://www.nin.res.in/RDA_Full_Report_2024.html";
    private final FoodNutritionRepository foods;
    public NutritionReportService(FoodNutritionRepository foods) { this.foods=foods; }
    public List<FoodNutrition> visibleFoods(String owner) { return foods.findVisible(owner); }

    public ReportDtos.Day day(Child child, LocalDate date, List<Meal> meals, String owner) {
        return day(child,date,meals,visibleFoods(owner));
    }
    public ReportDtos.Day day(Child child, LocalDate date, List<Meal> meals, List<FoodNutrition> catalog) {
        MealDtos.Totals totals=MealController.totals(meals);
        List<ReportDtos.NutrientReport> nutrients=nutrients(meals, catalog);
        Map<String,List<Meal>> groups=new LinkedHashMap<>();
        for (String type: List.of("Breakfast","Lunch","Dinner","Snack")) groups.put(type, new ArrayList<>());
        for (Meal meal: meals) groups.computeIfAbsent(meal.getMealType(), ignored -> new ArrayList<>()).add(meal);
        List<ReportDtos.MealGroup> breakdown=groups.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                .map(e -> new ReportDtos.MealGroup(e.getKey(),e.getValue().size(),MealController.totals(e.getValue()))).toList();
        return new ReportDtos.Day(date, totals, nutrients, breakdown, nutrients.stream().anyMatch(n -> !n.complete()));
    }

    public ReportDtos.DailyNutrition daily(Child child, LocalDate date, List<Meal> meals, String owner) {
        ReportDtos.Day d=day(child,date,meals,visibleFoods(owner));
        List<String> reasons=d.nutrients().stream().filter(n -> !n.complete()).map(n -> n.name()+" is incomplete because one or more food values are missing or use an unsupported unit.").distinct().toList();
        Integer age=child.getDateOfBirth()==null || date.isBefore(child.getDateOfBirth()) ? null : Period.between(child.getDateOfBirth(),date).getYears();
        return new ReportDtos.DailyNutrition(date,age,child.getGender(),d.nutrients(),d.mealBreakdown(),d.incomplete(),reasons,
                "These totals describe foods recorded in the app, not everything a child ate. A low logged total may reflect an incomplete food log; it does not establish a deficiency. References are unavailable when this app cannot select a reliable age- and sex-specific value.");
    }

    private List<ReportDtos.NutrientReport> nutrients(List<Meal> rows, List<FoodNutrition> catalog) {
        return List.of(
                tracked("Calories", "kcal", rows, Meal::getCalories, catalog, f->f.getCalories(), "Calories measure energy from food. Recorded calories are not an individualized energy prescription."),
                tracked("Protein", "g", rows, Meal::getProtein, catalog, f->f.getProtein(), "Protein supports growth and tissue repair."),
                tracked("Carbohydrates", "g", rows, Meal::getCarbohydrates, catalog, f->f.getCarbohydrates(), "Carbohydrates are one source of energy for the body."),
                tracked("Fat", "g", rows, Meal::getFat, catalog, f->f.getFat(), "Dietary fat provides energy and supports normal body functions."),
                tracked("Fibre", "g", rows, Meal::getFiber, catalog, f->f.getFiber(), "Fibre from plant foods supports regular digestion."),
                tracked("Sugar", "g", rows, Meal::getSugar, catalog, f->f.getSugar(), "This value represents sugar recorded for the logged foods."),
                tracked("Sodium", "mg", rows, Meal::getSodium, catalog, f->f.getSodium(), "Sodium is a mineral found in foods; this is a recorded intake total."),
                tracked("Saturated fat", "g", rows, Meal::getSaturatedFat, catalog, f->f.getSaturatedFat(), "This is the saturated fat reported for logged foods."),
                tracked("Calcium", "mg", rows, null, catalog, FoodNutrition::getCalcium, "Calcium supports bone and tooth development."),
                tracked("Iron", "mg", rows, null, catalog, FoodNutrition::getIron, "Iron helps the body make haemoglobin."),
                tracked("Vitamin C", "mg", rows, null, catalog, FoodNutrition::getVitaminC, "Vitamin C has several roles in the body; this total includes only foods with a recorded value."),
                tracked("Folate", "µg", rows, null, catalog, FoodNutrition::getFolate, "Folate is a B vitamin; this total includes only foods with a recorded value.")
        );
    }

    private ReportDtos.NutrientReport tracked(String name,String unit,List<Meal> rows,
            Function<Meal,BigDecimal> saved, List<FoodNutrition> catalog, Function<FoodNutrition,BigDecimal> per100, String explanation) {
        BigDecimal value=BigDecimal.ZERO; boolean complete=true;
        for (Meal m: rows) {
            BigDecimal v;
            if (saved!=null) v=saved.apply(m);
            else v=scaledFoodValue(m,per100);
            if(v==null) { complete=false; continue; }
            value=value.add(v);
        }
        BigDecimal actual=complete?value.setScale(2,RoundingMode.HALF_UP):null;
        List<ReportDtos.FoodExample> examples=catalog.stream().filter(f -> per100.apply(f)!=null && per100.apply(f).signum()>0)
                .limit(4).map(f -> new ReportDtos.FoodExample(f.getFoodName(),Objects.toString(f.getDataQuality(),"STATUS_UNAVAILABLE"),Objects.toString(f.getSourceName(),"Source not recorded"))).toList();
        return new ReportDtos.NutrientReport(name,actual,unit,null,null,null,complete,false,explanation,examples);
    }
    private static BigDecimal scaledFoodValue(Meal meal, Function<FoodNutrition,BigDecimal> field) {
        FoodNutrition food=meal.getFood(); if(food==null || meal.getQuantity()==null) return null;
        String basis=food.getNutritionBasis(); if(basis==null) return null;
        String unit=basis.toLowerCase(Locale.ROOT).contains("ml")?"ml":"g";
        if(meal.getQuantityUnit()==null || !unit.equalsIgnoreCase(meal.getQuantityUnit())) return null;
        BigDecimal per100=field.apply(food); if(per100==null) return null;
        return per100.multiply(meal.getQuantity()).divide(new BigDecimal("100"),8,RoundingMode.HALF_UP);
    }
}
