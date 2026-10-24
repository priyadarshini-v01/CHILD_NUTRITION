package com.childnutrition.service;

import com.childnutrition.dto.AlertDtos;
import com.childnutrition.dto.GrowthDtos;
import com.childnutrition.dto.MealDtos;
import com.childnutrition.entity.Alert;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.GrowthRecord;
import com.childnutrition.entity.Meal;
import com.childnutrition.entity.NutritionTarget;
import com.childnutrition.repository.AlertRepository;
import com.childnutrition.repository.GrowthRecordRepository;
import com.childnutrition.repository.MealRepository;
import com.childnutrition.repository.NutritionTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class InsightsService {
    private final MealRepository meals; private final GrowthRecordRepository growth;
    private final NutritionTargetRepository targets; private final AlertRepository alerts;
    @Value("${nutrition.alert.low-threshold:0.8}") private BigDecimal lowThreshold;
    @Value("${nutrition.alert.high-threshold:1.2}") private BigDecimal highThreshold;
    public InsightsService(MealRepository meals, GrowthRecordRepository growth, NutritionTargetRepository targets, AlertRepository alerts) {
        this.meals=meals; this.growth=growth; this.targets=targets; this.alerts=alerts;
    }

    public List<String> recommendations(Child child, LocalDate day) {
        List<String> out=new ArrayList<>(); int age=Period.between(child.getDateOfBirth(), day).getYears();
        out.add("Nutrition needs change with age; review current guidance with your child's pediatric care team.");
        if (child.getAllergies()!=null && !child.getAllergies().isBlank()) out.add("Check ingredient labels and prevent exposure to listed allergies: " + child.getAllergies() + ".");
        if (child.getDietaryPreferences()!=null && !child.getDietaryPreferences().isBlank()) out.add("Plan meals around the recorded dietary preference: " + child.getDietaryPreferences() + ".");
        List<GrowthRecord> history=growth.findByChildIdOrderByRecordDateDesc(child.getId());
        if (history.isEmpty()) out.add("Add a height and weight measurement to follow growth over time.");
        else {
            GrowthRecord latest=history.get(0);
            out.add("Latest measurements: height " + latest.getHeightCm() + " cm, weight " + latest.getWeightKg()
                    + " kg, BMI " + latest.getBmi() + ". Children's BMI should be interpreted on age and sex specific growth charts by a clinician.");
            if (history.size()>1) {
                GrowthRecord previous=history.get(1);
                if (latest.getWeightKg().compareTo(previous.getWeightKg())<0)
                    out.add("Recorded weight is lower than the prior measurement; confirm the measurement and discuss growth concerns with a clinician.");
                if (latest.getHeightCm().compareTo(previous.getHeightCm())<0)
                    out.add("Recorded height is lower than the prior measurement; confirm the measurement technique and entry.");
            }
        }
        if (age<2) out.add("For children under two, use age-appropriate feeding guidance from the pediatric care team.");
        NutritionTarget target=targetFor(child, age);
        if (target==null) out.add("Add a nutrition target for the child's age group to enable intake comparisons.");
        else {
            MealDtos.Totals total=totals(child, day);
            if (below(total.calories(), target.getCalorieTarget())) out.add("Recorded calories are below the configured daily target so far; review meal entries and offer an age-appropriate balanced meal or snack.");
            if (below(total.protein(), target.getProteinTarget())) out.add("Recorded protein is below the configured daily target so far; consider suitable protein sources that fit the child's allergies and diet.");
            if (below(total.carbohydrates(), target.getCarbohydrateTarget())) out.add("Recorded carbohydrates are below the configured daily target so far; review meal entries for suitable grains, fruits, or other sources.");
            if (below(total.fat(), target.getFatTarget())) out.add("Recorded fat is below the configured daily target so far; review meal entries and age-appropriate sources with a clinician.");
            if (above(total.calories(), target.getCalorieTarget())) out.add("Recorded calories are above the configured daily target; check meal entries and portion quantities.");
            if (above(total.protein(), target.getProteinTarget())) out.add("Recorded protein is above the configured daily target; confirm the target and serving quantities.");
            if (above(total.carbohydrates(), target.getCarbohydrateTarget())) out.add("Recorded carbohydrates are above the configured daily target; check meal entries and portion quantities.");
            if (above(total.fat(), target.getFatTarget())) out.add("Recorded fat is above the configured daily target; check meal entries and portion quantities.");
            if (above(total.sugar(), new BigDecimal("50"))) out.add("Recorded sugar is above 50 g today; review drinks, sweets and packaged food portions.");
            if (above(total.sodium(), new BigDecimal("2000"))) out.add("Recorded sodium is above 2000 mg today; review salty and packaged food portions.");
            if (above(total.saturatedFat(), new BigDecimal("20"))) out.add("Recorded saturated fat is above 20 g today; review portions and food composition.");
        }
        return out;
    }

    @Transactional
    public List<AlertDtos.Response> alerts(Child child, LocalDate day) {
        NutritionTarget target=targetFor(child, Period.between(child.getDateOfBirth(), day).getYears());
        if (target!=null) {
            MealDtos.Totals total=totals(child, day);
            check(child, day, "CALORIES_BELOW_TARGET", "Recorded calorie intake is below the configured lower threshold.", total.calories(), target.getCalorieTarget(), false);
            check(child, day, "PROTEIN_BELOW_TARGET", "Recorded protein intake is below the configured lower threshold.", total.protein(), target.getProteinTarget(), false);
            check(child, day, "CARBOHYDRATES_BELOW_TARGET", "Recorded carbohydrate intake is below the configured lower threshold.", total.carbohydrates(), target.getCarbohydrateTarget(), false);
            check(child, day, "FAT_BELOW_TARGET", "Recorded fat intake is below the configured lower threshold.", total.fat(), target.getFatTarget(), false);
            check(child, day, "CALORIES_ABOVE_TARGET", "Recorded calorie intake is above the configured upper threshold.", total.calories(), target.getCalorieTarget(), true);
            check(child, day, "PROTEIN_ABOVE_TARGET", "Recorded protein intake is above the configured upper threshold.", total.protein(), target.getProteinTarget(), true);
            check(child, day, "CARBOHYDRATES_ABOVE_TARGET", "Recorded carbohydrate intake is above the configured upper threshold.", total.carbohydrates(), target.getCarbohydrateTarget(), true);
            check(child, day, "FAT_ABOVE_TARGET", "Recorded fat intake is above the configured upper threshold.", total.fat(), target.getFatTarget(), true);
            check(child, day, "SUGAR_ABOVE_GUIDANCE", "Recorded sugar is above the configured daily guidance of 50 g.", total.sugar(), new BigDecimal("50"), true);
            check(child, day, "SODIUM_ABOVE_GUIDANCE", "Recorded sodium is above the configured daily guidance of 2000 mg.", total.sodium(), new BigDecimal("2000"), true);
            check(child, day, "SATURATED_FAT_ABOVE_GUIDANCE", "Recorded saturated fat is above the configured daily guidance of 20 g.", total.saturatedFat(), new BigDecimal("20"), true);
        }
        return alerts.findByChildIdOrderByCreatedAtDesc(child.getId()).stream().map(AlertDtos.Response::from).toList();
    }

    private void check(Child child, LocalDate day, String type, String message, BigDecimal intake, BigDecimal target, boolean upper) {
        if (intake == null || target == null || target.signum()<=0 || (upper
                ? intake.compareTo(target.multiply(highThreshold))<=0
                : intake.compareTo(target.multiply(lowThreshold))>=0)) return;
        if (!alerts.existsByChildIdAndAlertTypeAndAlertDate(child.getId(), type, day)) {
            Alert alert=new Alert(); alert.setChild(child); alert.setAlertType(type); alert.setMessage(message);
            alert.setAlertDate(day); alert.setSeverity("INFO"); alerts.save(alert);
        }
    }
    private NutritionTarget targetFor(Child child, int age) {
        String group=age<1?"0-1":age<4?"1-3":age<9?"4-8":age<14?"9-13":"14-18";
        return targets.findByChildIdOrderByAgeGroupAsc(child.getId()).stream()
                .filter(t -> t.getAgeGroup().equals(group)).findFirst().orElse(null);
    }
    private MealDtos.Totals totals(Child child, LocalDate day) {
        List<Meal> rows=meals.findByChildIdAndMealDate(child.getId(), day);
        return com.childnutrition.controller.MealController.totals(rows);
    }
    private boolean below(BigDecimal intake, BigDecimal target) {
        return intake != null && target != null && target.signum()>0 && intake.compareTo(target.multiply(lowThreshold))<0;
    }
    private boolean above(BigDecimal intake, BigDecimal target) {
        return intake != null && target != null && target.signum()>0 && intake.compareTo(target.multiply(highThreshold))>0;
    }
}
