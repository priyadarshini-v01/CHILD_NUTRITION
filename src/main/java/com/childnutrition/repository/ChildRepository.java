package com.childnutrition.repository;

import com.childnutrition.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {
    List<Child> findByParentId(Long parentId);
    List<Child> findByParentEmailOrderByNameAsc(String email);
    Optional<Child> findByIdAndParentEmail(Long id, String email);
}
