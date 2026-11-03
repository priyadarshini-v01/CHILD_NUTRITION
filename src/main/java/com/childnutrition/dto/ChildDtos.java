package com.childnutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ChildDtos {
    private ChildDtos() { }
    public record Request(@NotBlank(message="Please enter the child's name.") @Size(max = 120) String name,
                          @NotNull(message="Please enter a valid date of birth.") @PastOrPresent LocalDate dateOfBirth,
                          @NotBlank @Size(max = 30) String gender,
                          @Size(max = 4000) String allergies, @Size(max = 4000) String dietaryPreferences) { }
    public record Response(Long id, String name, LocalDate dateOfBirth, String gender, String allergies,
                           String dietaryPreferences, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static Response from(com.childnutrition.entity.Child child) {
            return new Response(child.getId(), child.getName(), child.getDateOfBirth(), child.getGender(),
                    child.getAllergies(), child.getDietaryPreferences(), child.getCreatedAt(), child.getUpdatedAt());
        }
    }
}
