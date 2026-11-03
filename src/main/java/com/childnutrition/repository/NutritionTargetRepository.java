package com.childnutrition.repository;

import com.childnutrition.entity.NutritionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface NutritionTargetRepository extends JpaRepository<NutritionTarget, Long> {
    Optional<NutritionTarget> findByAgeGroup(String ageGroup);
    List<NutritionTarget> findByChildIdOrderByAgeGroupAsc(Long childId);
    Optional<NutritionTarget> findByIdAndChildId(Long id, Long childId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from NutritionTarget t where t.child.id = :childId and t.ageGroup = :ageGroup")
    List<NutritionTarget> findByChildIdAndAgeGroupForUpdate(@Param("childId") Long childId, @Param("ageGroup") String ageGroup);
}
