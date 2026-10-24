package com.childnutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ChildDtos {
    private ChildDtos() { }
    public record Request(@NotBlank @Size(max = 120) String name, @NotNull @PastOrPresent LocalDate dateOfBirth,
                          @NotBlank @Size(max = 30) String gender, String allergies, String dietaryPreferences) { }
    public record Response(Long id, String name, LocalDate dateOfBirth, String gender, String allergies,
                           String dietaryPreferences, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static Response from(com.childnutrition.entity.Child child) {
            return new Response(child.getId(), child.getName(), child.getDateOfBirth(), child.getGender(),
                    child.getAllergies(), child.getDietaryPreferences(), child.getCreatedAt(), child.getUpdatedAt());
        }
    }
}
