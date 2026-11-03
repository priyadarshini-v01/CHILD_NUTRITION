package com.childnutrition.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Small per-account request guard for the optional metered model endpoint. */
@Component
public class NutritionAssistantRateLimiter {
    private record Window(long startedAt, int count) { }
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public NutritionAssistantRateLimiter(@Value("${nutrition.assistant.rate-limit-per-minute:20}") int limit) {
        this.limit = Math.max(1, limit);
    }

    public void check(String account) {
        long now = Instant.now().toEpochMilli();
        Window updated = windows.compute(account, (key, previous) -> previous == null || now - previous.startedAt() >= 60_000
                ? new Window(now, 1) : new Window(previous.startedAt(), previous.count() + 1));
        if (updated.count() > limit)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before asking another question.");
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > 60_000);
    }
}
