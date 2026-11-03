package com.childnutrition.controller;

import com.childnutrition.dto.GrowthDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.entity.GrowthRecord;
import com.childnutrition.repository.GrowthRecordRepository;
import com.childnutrition.service.ChildAccessService;
import com.childnutrition.service.GrowthMeasurementValidator;
import com.childnutrition.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/children/{childId}/growth")
public class GrowthController {
    private final GrowthRecordRepository records; private final ChildAccessService access;
    private final GrowthMeasurementValidator validator;
    private final JwtService jwt;
    private final double sameDayHeightTolerance, sameDayWeightTolerance;
    private final double heightUnder2, height2to6, height6to12, height12to18;
    private final double weightUnder2, weight2to6, weight6to12, weight12to18;
    public GrowthController(GrowthRecordRepository records, ChildAccessService access, GrowthMeasurementValidator validator, JwtService jwt,
            @Value("${growth.validation.same-day-height-tolerance-cm:1.0}") double sameDayHeightTolerance,
            @Value("${growth.validation.same-day-weight-tolerance-kg:0.5}") double sameDayWeightTolerance,
            @Value("${growth.validation.height-rate-under-2-cm-year:30}") double heightUnder2,
            @Value("${growth.validation.height-rate-2-to-6-cm-year:15}") double height2to6,
            @Value("${growth.validation.height-rate-6-to-12-cm-year:10}") double height6to12,
            @Value("${growth.validation.height-rate-12-to-18-cm-year:15}") double height12to18,
            @Value("${growth.validation.weight-rate-under-2-kg-year:8}") double weightUnder2,
            @Value("${growth.validation.weight-rate-2-to-6-kg-year:5}") double weight2to6,
            @Value("${growth.validation.weight-rate-6-to-12-kg-year:10}") double weight6to12,
            @Value("${growth.validation.weight-rate-12-to-18-kg-year:15}") double weight12to18) {
        this.records = records; this.access = access; this.validator = validator; this.jwt = jwt;
        this.sameDayHeightTolerance = sameDayHeightTolerance; this.sameDayWeightTolerance = sameDayWeightTolerance;
        this.heightUnder2=heightUnder2; this.height2to6=height2to6; this.height6to12=height6to12; this.height12to18=height12to18;
        this.weightUnder2=weightUnder2; this.weight2to6=weight2to6; this.weight6to12=weight6to12; this.weight12to18=weight12to18;
    }
    @PostMapping @Transactional
    public ResponseEntity<?> create(@PathVariable Long childId, @Valid @RequestBody GrowthDtos.Request request, Authentication auth) {
        access.require(childId, auth); Child child = access.lockForTargetChange(childId); validator.validate(child, request);
        List<GrowthRecord> history = records.findHistoryForUpdate(childId);
        ResponseEntity<?> challenge = challenge(childId, child, request, history, null);
        if (challenge != null) return challenge;
        GrowthRecord row = new GrowthRecord(); row.setChild(child); apply(row, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GrowthDtos.Response.from(records.save(row)));
    }
    @GetMapping public List<GrowthDtos.Response> history(@PathVariable Long childId, Authentication auth) {
        Child child = access.require(childId, auth);
        return records.findByChildIdOrderByRecordDateDescIdDesc(child.getId()).stream().map(GrowthDtos.Response::from).toList();
    }
    @PutMapping("/{recordId}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long childId, @PathVariable Long recordId,
            @Valid @RequestBody GrowthDtos.Request request, Authentication auth) {
        access.require(childId, auth); Child child = access.lockForTargetChange(childId); validator.validate(child, request);
        GrowthRecord row = records.findByIdAndChildIdForUpdate(recordId, childId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Growth record not found"));
        List<GrowthRecord> history = records.findHistoryForUpdate(childId);
        ResponseEntity<?> challenge = challenge(childId, child, request, history, recordId);
        if (challenge != null) return challenge;
        apply(row, request); return ResponseEntity.ok(GrowthDtos.Response.from(records.save(row)));
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
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Growth record ID must be a positive number.");
        return records.findByIdAndChildId(id, child.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Growth record not found"));
    }
    private static void apply(GrowthRecord r, GrowthDtos.Request q) {
        r.setRecordDate(q.recordDate()); r.setHeightCm(q.heightCm()); r.setWeightKg(q.weightKg()); r.setNotes(q.notes());
    }

    private ResponseEntity<?> challenge(Long childId, Child child, GrowthDtos.Request request, List<GrowthRecord> history, Long replacingId) {
        List<GrowthRecord> ordered = history.stream().filter(r -> !r.getId().equals(replacingId))
                .sorted(java.util.Comparator.comparing(GrowthRecord::getRecordDate).thenComparing(GrowthRecord::getId)).toList();
        GrowthRecord previous = null; GrowthRecord next = null;
        for (GrowthRecord r : ordered) {
            if (!r.getRecordDate().isAfter(request.recordDate())) previous = r;
            else if (next == null) next = r;
        }
        boolean suspicious = suspicious(previous, request, child) || suspicious(next, request, child);
        String fingerprint = fingerprint(childId, request, replacingId, previous, next);
        boolean supplied = request.confirmationToken() != null && !request.confirmationToken().isBlank();
        if (supplied && jwt.isGrowthConfirmationValid(request.confirmationToken(), childId, fingerprint)) return null;
        if (!suspicious && !supplied) return null;
        String code = supplied ? "CONFIRMATION_STALE" : "CONFIRMATION_REQUIRED";
        GrowthRecord comparison = previous != null ? previous : next;
        String msg = comparison == null ? "Measurement history changed. Please review and confirm again."
                : "⚠️ Unusual measurement change detected. The previous measurement for this date was "
                + comparison.getHeightCm().stripTrailingZeros().toPlainString() + " cm and "
                + comparison.getWeightKg().stripTrailingZeros().toPlainString() + " kg. You entered "
                + request.heightCm().stripTrailingZeros().toPlainString() + " cm and "
                + request.weightKg().stripTrailingZeros().toPlainString() + " kg. Please verify the height and weight before saving.";
        var response = new GrowthDtos.SaveChallenge(code, msg, jwt.issueGrowthConfirmation(childId, fingerprint),
                comparison == null ? null : new GrowthDtos.Measurement(comparison.getId(), comparison.getRecordDate(), comparison.getHeightCm(), comparison.getWeightKg()),
                new GrowthDtos.Measurement(replacingId, request.recordDate(), request.heightCm(), request.weightKg()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    private boolean suspicious(GrowthRecord old, GrowthDtos.Request q, Child child) {
        if (old == null) return false;
        long days = Math.max(0, Math.abs(ChronoUnit.DAYS.between(old.getRecordDate(), q.recordDate())));
        double years = days / 365.0;
        int age = java.time.Period.between(child.getDateOfBirth(), q.recordDate()).getYears();
        double heightRate = age < 2 ? heightUnder2 : age < 6 ? height2to6 : age < 12 ? height6to12 : height12to18;
        double weightRate = age < 2 ? weightUnder2 : age < 6 ? weight2to6 : age < 12 ? weight6to12 : weight12to18;
        double hLimit = days == 0 ? sameDayHeightTolerance : 0.75 + heightRate * years;
        double wLimit = days == 0 ? sameDayWeightTolerance : 0.5 + weightRate * years;
        return old.getHeightCm().subtract(q.heightCm()).abs().doubleValue() > hLimit
                || old.getWeightKg().subtract(q.weightKg()).abs().doubleValue() > wLimit;
    }
    private static String fingerprint(Long childId, GrowthDtos.Request q, Long replacing, GrowthRecord previous, GrowthRecord next) {
        String text = childId + "|" + q.recordDate() + "|" + q.heightCm().stripTrailingZeros().toPlainString() + "|" + q.weightKg().stripTrailingZeros().toPlainString() + "|" + replacing + "|" + snap(previous) + "|" + snap(next);
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static String snap(GrowthRecord r) { return r == null ? "-" : r.getId()+":"+r.getRecordDate()+":"+r.getHeightCm()+":"+r.getWeightKg(); }
}
