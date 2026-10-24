package com.childnutrition.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "meals")
@Getter
@Setter
@NoArgsConstructor
public class Meal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @NotNull
    @Column(nullable = false)
    private LocalDate mealDate;

    @Column
    private LocalTime mealTime;

    @NotBlank
    @Column(nullable = false, length = 40)
    private String mealType;

    @NotBlank
    @Column(nullable = false, length = 180)
    private String foodName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private FoodNutrition food;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;
    @Column(name = "quantity_unit", length = 10)
    private String quantityUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal calories;

    @Column(precision = 10, scale = 2)
    private BigDecimal protein;

    @Column(precision = 10, scale = 2)
    private BigDecimal carbohydrates;

    @Column(precision = 10, scale = 2)
    private BigDecimal fat;
    @Column(precision = 10, scale = 2) private BigDecimal sugar;
    @Column(precision = 10, scale = 2) private BigDecimal fiber;
    @Column(precision = 10, scale = 2) private BigDecimal sodium;
    @Column(name = "saturated_fat", precision = 10, scale = 2) private BigDecimal saturatedFat;

    @Column(columnDefinition = "TEXT")
    private String vitamins;

    @Column(columnDefinition = "TEXT")
    private String minerals;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
