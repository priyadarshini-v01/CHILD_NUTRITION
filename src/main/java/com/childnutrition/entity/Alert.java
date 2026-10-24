package com.childnutrition.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "alerts", uniqueConstraints = @UniqueConstraint(name = "uk_alert_child_type_date",
        columnNames = {"child_id", "alert_type", "alert_date"}))
@Getter
@Setter
@NoArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String alertType;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @NotBlank
    @Column(nullable = false, length = 30)
    private String severity;

    @NotNull
    @Column(name = "alert_date", nullable = false)
    private LocalDate alertDate;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
