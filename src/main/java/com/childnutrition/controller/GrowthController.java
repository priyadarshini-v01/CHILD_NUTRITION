package com.childnutrition.controller;

import com.childnutrition.dto.GrowthDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.GrowthRecord;
import com.childnutrition.repository.GrowthRecordRepository;
import com.childnutrition.service.ChildAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/children/{childId}/growth")
public class GrowthController {
    private final GrowthRecordRepository records; private final ChildAccessService access;
    public GrowthController(GrowthRecordRepository records, ChildAccessService access) { this.records = records; this.access = access; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public GrowthDtos.Response create(@PathVariable Long childId, @Valid @RequestBody GrowthDtos.Request request, Authentication auth) {
        Child child = access.require(childId, auth); GrowthRecord row = new GrowthRecord(); row.setChild(child); apply(row, request);
        return GrowthDtos.Response.from(records.save(row));
    }
    @GetMapping public List<GrowthDtos.Response> history(@PathVariable Long childId, Authentication auth) {
        Child child = access.require(childId, auth);
        return records.findByChildIdOrderByRecordDateDesc(child.getId()).stream().map(GrowthDtos.Response::from).toList();
    }
    @PutMapping("/{recordId}")
    public GrowthDtos.Response update(@PathVariable Long childId, @PathVariable Long recordId,
            @Valid @RequestBody GrowthDtos.Request request, Authentication auth) {
        Child child = access.require(childId, auth); GrowthRecord row = require(child, recordId); apply(row, request);
        return GrowthDtos.Response.from(records.save(row));
    }
    @DeleteMapping("/{recordId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long childId, @PathVariable Long recordId, Authentication auth) {
        Child child = access.require(childId, auth); records.delete(require(child, recordId));
    }
    @GetMapping("/summary")
    public GrowthDtos.Summary summary(@PathVariable Long childId, Authentication auth) {
        Child child = access.require(childId, auth); List<GrowthRecord> rows = records.findByChildIdOrderByRecordDateDesc(child.getId());
        if (rows.isEmpty()) return new GrowthDtos.Summary(null, BigDecimal.ZERO, BigDecimal.ZERO);
        GrowthRecord latest = rows.get(0); GrowthRecord oldest = rows.get(rows.size()-1);
        return new GrowthDtos.Summary(GrowthDtos.Response.from(latest), latest.getHeightCm().subtract(oldest.getHeightCm()),
                latest.getWeightKg().subtract(oldest.getWeightKg()));
    }
    private GrowthRecord require(Child child, Long id) {
        return records.findByIdAndChildId(id, child.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Growth record not found"));
    }
    private static void apply(GrowthRecord r, GrowthDtos.Request q) {
        r.setRecordDate(q.recordDate()); r.setHeightCm(q.heightCm()); r.setWeightKg(q.weightKg()); r.setNotes(q.notes());
    }
}
