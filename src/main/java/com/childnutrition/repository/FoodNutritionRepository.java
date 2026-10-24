package com.childnutrition.repository;

import com.childnutrition.entity.FoodNutrition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FoodNutritionRepository extends JpaRepository<FoodNutrition, Long> {
    @Query("select f from FoodNutrition f left join f.customOwner owner where f.active = true and (owner is null or lower(owner.email) = lower(:owner)) order by f.category, f.foodName")
    List<FoodNutrition> findVisible(@Param("owner") String owner);
    @Query("select f from FoodNutrition f left join f.customOwner owner where f.active = true and (:category is null or f.category = :category) and (owner is null or lower(owner.email) = lower(:owner)) order by f.category, f.foodName")
    List<FoodNutrition> findVisibleByCategory(@Param("owner") String owner, @Param("category") String category);
    @Query("select f from FoodNutrition f left join f.customOwner owner where f.active = true and lower(f.foodName) like lower(concat('%', :query, '%')) and (owner is null or lower(owner.email) = lower(:owner)) order by f.foodName")
    List<FoodNutrition> searchVisible(@Param("owner") String owner, @Param("query") String query, org.springframework.data.domain.Pageable page);
    @Query("select f from FoodNutrition f left join f.customOwner owner where f.active = true and lower(f.foodName) like lower(concat('%', :query, '%')) and f.category = :category and (owner is null or lower(owner.email) = lower(:owner)) order by f.foodName")
    List<FoodNutrition> searchVisibleByCategory(@Param("owner") String owner, @Param("query") String query, @Param("category") String category, org.springframework.data.domain.Pageable page);
    @Query("select f from FoodNutrition f left join f.customOwner owner where f.id = :id and f.active = true and (owner is null or lower(owner.email) = lower(:owner))")
    Optional<FoodNutrition> findVisibleById(@Param("id") Long id, @Param("owner") String owner);
    boolean existsByFoodNameIgnoreCaseAndCustomOwnerEmail(String foodName, String email);
    @Query("select f from FoodNutrition f where lower(f.foodName) = lower(:name) and f.customOwner is null")
    Optional<FoodNutrition> findCatalogByName(@Param("name") String name);

    @Query("select distinct f.category from FoodNutrition f where f.active = true and f.category is not null order by f.category")
    List<String> findActiveCategories();

    @Modifying
    @Query("update FoodNutrition f set f.active = false where f.active = true and f.customOwner is null")
    int deactivateAll();

}
