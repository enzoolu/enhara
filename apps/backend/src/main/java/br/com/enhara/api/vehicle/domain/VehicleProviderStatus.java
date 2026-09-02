package br.com.enhara.api.vehicle.domain;

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
@Table(name = "vehicle_provider_statuses")
public class VehicleProviderStatus {
    public enum State { LIVE, CACHE_FRESH, CACHE_STALE, UNAVAILABLE, CONFLICT, NOT_REQUESTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private State state;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "data_fetched_at")
    private Instant dataFetchedAt;

    protected VehicleProviderStatus() {
    }

    public VehicleProviderStatus(UUID vehicleId, String provider, State state, String message,
                                 Instant checkedAt, Instant dataFetchedAt) {
        this.vehicleId = vehicleId;
        this.provider = provider;
        update(state, message, checkedAt, dataFetchedAt);
    }

    public void update(State state, String message, Instant checkedAt, Instant dataFetchedAt) {
        this.state = state;
        this.message = message;
        this.checkedAt = checkedAt;
        this.dataFetchedAt = dataFetchedAt;
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public String getProvider() { return provider; }
    public State getState() { return state; }
    public String getMessage() { return message; }
    public Instant getCheckedAt() { return checkedAt; }
    public Instant getDataFetchedAt() { return dataFetchedAt; }
}
