package com.childnutrition.repository;

import com.childnutrition.entity.GrowthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, Long> {
    List<GrowthRecord> findByChildIdOrderByRecordDateDesc(Long childId);
    List<GrowthRecord> findByChildIdOrderByRecordDateDescIdDesc(Long childId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrowthRecord g where g.child.id = :childId order by g.recordDate, g.id")
    List<GrowthRecord> findHistoryForUpdate(@Param("childId") Long childId);
    Optional<GrowthRecord> findByIdAndChildId(Long id, Long childId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrowthRecord g where g.id = :id and g.child.id = :childId")
    Optional<GrowthRecord> findByIdAndChildIdForUpdate(@Param("id") Long id, @Param("childId") Long childId);
}
