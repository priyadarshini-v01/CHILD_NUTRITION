package com.childnutrition.dto;

import com.childnutrition.entity.Alert;
import java.time.LocalDateTime;

public final class AlertDtos {
    private AlertDtos() { }
    public record Response(Long id, String alertType, String message, String severity, boolean read, LocalDateTime createdAt) {
        public static Response from(Alert a) { return new Response(a.getId(), a.getAlertType(), a.getMessage(), a.getSeverity(), a.isRead(), a.getCreatedAt()); }
    }
}
