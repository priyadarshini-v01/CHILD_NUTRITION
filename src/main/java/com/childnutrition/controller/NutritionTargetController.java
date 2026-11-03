package com.childnutrition.controller;

import com.childnutrition.dto.NutritionTargetDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.NutritionTarget;
import com.childnutrition.repository.NutritionTargetRepository;
import com.childnutrition.service.ChildAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/children/{childId}/nutrition-targets")
public class NutritionTargetController {
    private final NutritionTargetRepository targets; private final ChildAccessService access;
    public NutritionTargetController(NutritionTargetRepository targets, ChildAccessService access) { this.targets=targets; this.access=access; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public NutritionTargetDtos.Response create(@PathVariable Long childId, @Valid @RequestBody NutritionTargetDtos.Request request, Authentication auth) {
        Child child=access.require(childId, auth);
        access.lockForTargetChange(child.getId());
        if (!targets.findByChildIdAndAgeGroupForUpdate(child.getId(), request.ageGroup().trim()).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A nutrition target already exists for this age group.");
        NutritionTarget target=new NutritionTarget(); target.setChild(child); apply(target, request);
        return NutritionTargetDtos.Response.from(targets.save(target));
    }
    @GetMapping public List<NutritionTargetDtos.Response> list(@PathVariable Long childId, Authentication auth) {
        Child child=access.require(childId, auth); return targets.findByChildIdOrderByAgeGroupAsc(child.getId()).stream().map(NutritionTargetDtos.Response::from).toList();
    }
    @PutMapping("/{targetId}")
    @Transactional
    public NutritionTargetDtos.Response update(@PathVariable Long childId, @PathVariable Long targetId,
            @Valid @RequestBody NutritionTargetDtos.Request request, Authentication auth) {
        if (targetId == null || targetId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nutrition target ID must be a positive number.");
        Child child=access.require(childId, auth); NutritionTarget t=targets.findByIdAndChildId(targetId, child.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition target not found"));
        access.lockForTargetChange(child.getId());
        if (targets.findByChildIdAndAgeGroupForUpdate(child.getId(), request.ageGroup().trim()).stream().anyMatch(existing -> !existing.getId().equals(targetId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A nutrition target already exists for this age group.");
        apply(t, request); return NutritionTargetDtos.Response.from(targets.save(t));
    }
    @DeleteMapping("/{targetId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long childId, @PathVariable Long targetId, Authentication auth) {
        if (targetId == null || targetId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nutrition target ID must be a positive number.");
        Child child=access.require(childId, auth); NutritionTarget t=targets.findByIdAndChildId(targetId, child.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutrition target not found")); targets.delete(t);
    }
    private static void apply(NutritionTarget t, NutritionTargetDtos.Request r) {
        t.setAgeGroup(r.ageGroup().trim()); t.setCalorieTarget(r.calorieTarget()); t.setProteinTarget(r.proteinTarget());
        t.setCarbohydrateTarget(r.carbohydrateTarget()); t.setFatTarget(r.fatTarget());
    }
}
