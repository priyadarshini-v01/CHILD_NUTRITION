package com.childnutrition.repository;

import com.childnutrition.entity.NutritionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface NutritionTargetRepository extends JpaRepository<NutritionTarget, Long> {
    Optional<NutritionTarget> findByAgeGroup(String ageGroup);
    List<NutritionTarget> findByChildIdOrderByAgeGroupAsc(Long childId);
    Optional<NutritionTarget> findByIdAndChildId(Long id, Long childId);
}
