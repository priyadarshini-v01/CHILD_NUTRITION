package com.childnutrition.service;

import com.childnutrition.dto.GrowthDtos;
import com.childnutrition.entity.Child;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

/** Server authority for expected units and broad age plausibility checks. */
@Component
public class GrowthMeasurementValidator {
    private final BigDecimal minHeight;
    private final BigDecimal maxHeight;
    private final BigDecimal minWeight;
    private final BigDecimal maxWeight;

    public GrowthMeasurementValidator(
            @Value("${growth.validation.min-height-cm:30}") BigDecimal minHeight,
            @Value("${growth.validation.max-height-cm:250}") BigDecimal maxHeight,
            @Value("${growth.validation.min-weight-kg:0.5}") BigDecimal minWeight,
            @Value("${growth.validation.max-weight-kg:300}") BigDecimal maxWeight) {
        this.minHeight = minHeight; this.maxHeight = maxHeight;
        this.minWeight = minWeight; this.maxWeight = maxWeight;
    }

    public void validate(Child child, GrowthDtos.Request request) {
        BigDecimal height = request.heightCm(), weight = request.weightKg();
        if (height == null || weight == null || height.signum() <= 0 || weight.signum() <= 0)
            reject("Invalid height or weight. Please enter valid positive values in the expected units.");
        if (request.recordDate() == null)
            reject("Please enter a measurement date.");
        if (request.recordDate().isAfter(LocalDate.now()))
            reject("The measurement date cannot be in the future.");
        if (height.compareTo(minHeight) < 0 || height.compareTo(maxHeight) > 0)
            reject("Please verify the height. The entered value may use the wrong unit.");
        if (weight.compareTo(minWeight) < 0 || weight.compareTo(maxWeight) > 0)
            reject("Please verify the weight. Enter weight in kilograms, not grams.");

        if (child == null || child.getDateOfBirth() == null)
            reject("Please add or correct the child's date of birth before recording this measurement.");
        if (request.recordDate().isBefore(child.getDateOfBirth()))
            reject("The measurement date cannot be before the child's date of birth.");
        int age = Period.between(child.getDateOfBirth(), request.recordDate()).getYears();
        // Adults use the general unit bounds above; child growth ranges do not apply at 18.
        if (age >= 18) return;
        // Broad plausibility windows catch obvious unit/decimal mistakes without using BMI as a rejection rule.
        BigDecimal ageMinHeight = age < 1 ? bd(40) : age < 3 ? bd(50) : age < 6 ? bd(60) : age < 13 ? bd(80) : bd(100);
        BigDecimal ageMaxHeight = age < 1 ? bd(100) : age < 3 ? bd(120) : age < 6 ? bd(150) : age < 13 ? bd(210) : bd(230);
        BigDecimal ageMinWeight = age < 1 ? bd(1) : age < 3 ? bd(2) : age < 6 ? bd(5) : age < 13 ? bd(10) : bd(20);
        BigDecimal ageMaxWeight = age < 1 ? bd(30) : age < 3 ? bd(40) : age < 6 ? bd(80) : age < 13 ? bd(180) : bd(250);
        if (height.compareTo(ageMinHeight) < 0 || height.compareTo(ageMaxHeight) > 0
                || weight.compareTo(ageMinWeight) < 0 || weight.compareTo(ageMaxWeight) > 0)
            reject("The height and weight values appear inconsistent with the child's age. Please verify the measurements.");
    }

    private static BigDecimal bd(int value) { return BigDecimal.valueOf(value); }
    private static void reject(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
