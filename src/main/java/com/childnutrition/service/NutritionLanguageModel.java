package com.childnutrition.service;

import com.childnutrition.dto.NutritionAssistantDtos;
import java.util.List;

/** Provider-independent boundary for optional, grounded nutrition language generation. */
public interface NutritionLanguageModel {
    ModelAnswer answer(String question, List<NutritionAssistantDtos.Turn> history, String context);

    record ModelAnswer(String text, List<NutritionAssistantDtos.Reference> references) { }
}
