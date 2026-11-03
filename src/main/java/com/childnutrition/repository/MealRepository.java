package com.childnutrition.repository;

import com.childnutrition.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByChildId(Long childId);
    List<Meal> findByChildIdAndMealDate(Long childId, LocalDate mealDate);
    List<Meal> findByChildIdAndMealDateBetweenOrderByMealDateAscMealTimeAsc(Long childId, LocalDate start, LocalDate end);
    @Query("select m from Meal m left join fetch m.food where m.child.id = :childId and m.mealDate = :day order by m.mealTime, m.id")
    List<Meal> findReportMealsForDay(@Param("childId") Long childId, @Param("day") LocalDate day);
    @Query("select m from Meal m left join fetch m.food where m.child.id = :childId and m.mealDate between :start and :end order by m.mealDate, m.mealTime, m.id")
    List<Meal> findReportMealsForPeriod(@Param("childId") Long childId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    Optional<Meal> findByIdAndChildId(Long id, Long childId);
}
