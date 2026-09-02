package br.com.enhara.api.notes.domain;

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
@Table(name = "vehicle_notes")
public class VehicleNote {
    public enum Category { MAINTENANCE, DOCUMENTATION, GENERAL }
    public enum Status { OPEN, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Category category;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected VehicleNote() {
    }

    public VehicleNote(UUID vehicleId, String title, String description, Category category, Instant dueAt, Instant now) {
        this.vehicleId = vehicleId;
        update(title, description, category, dueAt, now);
        this.status = Status.OPEN;
        this.createdAt = now;
    }

    public void update(String title, String description, Category category, Instant dueAt, Instant now) {
        this.title = title.trim();
        this.description = description.trim();
        this.category = category;
        this.dueAt = dueAt;
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        this.status = Status.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void reopen(Instant now) {
        this.status = Status.OPEN;
        this.completedAt = null;
        this.updatedAt = now;
    }

    public boolean isOverdue(Instant now) {
        return status == Status.OPEN && dueAt != null && dueAt.isBefore(now);
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public Instant getDueAt() { return dueAt; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
