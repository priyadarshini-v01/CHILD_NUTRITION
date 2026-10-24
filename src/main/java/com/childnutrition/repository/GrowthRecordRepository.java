package com.childnutrition.repository;

import com.childnutrition.entity.GrowthRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, Long> {
    List<GrowthRecord> findByChildIdOrderByRecordDateDesc(Long childId);
    Optional<GrowthRecord> findByIdAndChildId(Long id, Long childId);
}
