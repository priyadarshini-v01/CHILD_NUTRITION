package com.childnutrition.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "growth_records")
@Getter
@Setter
@NoArgsConstructor
public class GrowthRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @NotNull
    @Column(nullable = false)
    private LocalDate recordDate;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal heightCm;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(precision = 6, scale = 2)
    private BigDecimal bmi;

    @Column(length = 40)
    private String bmiCategory;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    @PreUpdate
    void calculateBmi() {
        if (heightCm != null && weightKg != null && heightCm.signum() > 0) {
            java.math.BigDecimal heightM = heightCm.movePointLeft(2);
            bmi = weightKg.divide(heightM.multiply(heightM), 2, java.math.RoundingMode.HALF_UP);
        }
    }
}
