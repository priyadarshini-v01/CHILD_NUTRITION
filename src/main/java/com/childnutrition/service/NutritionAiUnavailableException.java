package com.childnutrition.service;

public class NutritionAiUnavailableException extends RuntimeException {
    public NutritionAiUnavailableException() { super("Nutrition assistant provider unavailable"); }
}
