package com.childnutrition.repository;

import com.childnutrition.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByChildIdOrderByCreatedAtDesc(Long childId);
    boolean existsByChildIdAndAlertTypeAndAlertDate(Long childId, String alertType, LocalDate alertDate);
    Optional<Alert> findByIdAndChildId(Long id, Long childId);
}
