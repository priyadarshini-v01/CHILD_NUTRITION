package com.childnutrition.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "food_nutrition", uniqueConstraints = @UniqueConstraint(name = "uk_food_name_owner", columnNames = {"food_name", "custom_owner_id"}))
@Getter @Setter @NoArgsConstructor
public class FoodNutrition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "food_name", nullable = false, length = 180)
    private String foodName;
    @Column(precision = 10, scale = 2)
    private BigDecimal calories;
    @Column(precision = 10, scale = 2)
    private BigDecimal carbohydrates;
    @Column(precision = 10, scale = 2)
    private BigDecimal protein;
    @Column(precision = 10, scale = 2)
    private BigDecimal fat;
    @Column(name = "saturated_fat", precision = 10, scale = 2) private BigDecimal saturatedFat;
    @Column(length = 60)
    private String category;
    @Column(name = "default_meal_type", length = 40)
    private String defaultMealType;
    @Column(name = "serving_size", precision = 10, scale = 2)
    private BigDecimal servingSize;
    @Column(name = "serving_unit", length = 40)
    private String servingUnit;
    @Column(name = "serving_basis", length = 40)
    private String servingBasis;
    @Column(name = "source_name", length = 180)
    private String sourceName;
    @Column(name = "free_sugar", precision = 10, scale = 2)
    private BigDecimal sugar;
    @Column(precision = 10, scale = 2)
    private BigDecimal fiber;
    @Column(precision = 10, scale = 2)
    private BigDecimal sodium;
    @Column(precision = 10, scale = 2)
    private BigDecimal calcium;
    @Column(precision = 10, scale = 2)
    private BigDecimal iron;
    @Column(name = "vitamin_c", precision = 10, scale = 2)
    private BigDecimal vitaminC;
    @Column(precision = 10, scale = 2)
    private BigDecimal folate;
    @Column(name = "nutrition_basis", nullable = false, length = 40) private String nutritionBasis = "100 g";
    @Column(name = "data_quality", length = 30) private String dataQuality = "SOURCE_UNVERIFIED";
    @Column(name = "source_reference", length = 500) private String sourceReference;
    @Column(name = "source_retrieved_at") private LocalDate sourceRetrievedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_owner_id")
    private User customOwner;
    @Column
    private Boolean active;
}
