package com.childnutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.time.LocalDate;

public final class NutritionAssistantDtos {
    private NutritionAssistantDtos() { }
    public record Turn(@NotBlank @Size(max=1500) String role, @NotBlank @Size(max=3000) String content) { }
    public record Request(@NotBlank @Size(max=500) String question, Long childId,
                          @Size(max=10) List<@jakarta.validation.Valid Turn> history,
                          @jakarta.validation.constraints.PastOrPresent LocalDate reportDate) { }
    public record Reference(String name, String url) { }
    public record Response(String answer, List<FoodDtos.Response> foods, List<Reference> references,
                           List<String> suggestions, String dataNote, String answerMode) { }
}
