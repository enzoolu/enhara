package br.com.enhara.api.alerts.domain;

import br.com.enhara.api.diagnostics.domain.Diagnostic;
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
@Table(name = "alerts")
public class Alert {
    public enum Type { ENGINE_OVERHEAT, LOW_BATTERY, LOW_FUEL, ENGINE_OVERSPEED }
    public enum Status { OPEN, ACKNOWLEDGED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "telemetry_id")
    private Long telemetryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Diagnostic.Severity severity;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected Alert() {
    }

    public Alert(UUID vehicleId, Long telemetryId, Type type, Diagnostic.Severity severity,
                 String title, String message) {
        this.vehicleId = vehicleId;
        this.telemetryId = telemetryId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.status = Status.OPEN;
        this.createdAt = Instant.now();
    }

    public void acknowledge() {
        status = Status.ACKNOWLEDGED;
        acknowledgedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public Long getTelemetryId() { return telemetryId; }
    public Type getType() { return type; }
    public Diagnostic.Severity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
