package br.com.enhara.api.diagnostics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostics")
public class Diagnostic {
    public enum Severity { INFO, WARNING, CRITICAL }
    public enum Status { ACTIVE, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "telemetry_id")
    private Long telemetryId;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Diagnostic() {
    }

    public Diagnostic(UUID vehicleId, Long telemetryId, String code, String description, Severity severity) {
        this.vehicleId = vehicleId;
        this.telemetryId = telemetryId;
        this.code = code;
        this.description = description;
        this.severity = severity;
        this.status = Status.ACTIVE;
        this.detectedAt = Instant.now();
    }

    public void resolve() {
        status = Status.RESOLVED;
        resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public Long getTelemetryId() { return telemetryId; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public Status getStatus() { return status; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
