package com.childnutrition.repository;

import com.childnutrition.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByChildId(Long childId);
    List<Meal> findByChildIdAndMealDate(Long childId, LocalDate mealDate);
    List<Meal> findByChildIdAndMealDateBetweenOrderByMealDateAscMealTimeAsc(Long childId, LocalDate start, LocalDate end);
    Optional<Meal> findByIdAndChildId(Long id, Long childId);
}
