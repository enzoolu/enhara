package br.com.enhara.api.trips.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Column(name = "average_speed_kph", nullable = false)
    private double averageSpeedKph;

    @Column(name = "max_speed_kph", nullable = false)
    private double maxSpeedKph;

    @Column(name = "harsh_acceleration_count", nullable = false)
    private int harshAccelerationCount;

    @Column(name = "harsh_braking_count", nullable = false)
    private int harshBrakingCount;

    @Column(name = "high_rpm_seconds", nullable = false)
    private long highRpmSeconds;

    @Column(name = "driving_score", nullable = false)
    private int drivingScore;

    protected Trip() {
    }

    public Trip(UUID vehicleId, Instant startedAt) {
        this.vehicleId = vehicleId;
        this.startedAt = startedAt;
        this.distanceKm = 0;
        this.averageSpeedKph = 0;
        this.maxSpeedKph = 0;
        this.drivingScore = 100;
    }

    public void finish(Instant endedAt, TripMetrics metrics) {
        this.endedAt = endedAt;
        this.distanceKm = metrics.distanceKm();
        this.averageSpeedKph = metrics.averageSpeedKph();
        this.maxSpeedKph = metrics.maxSpeedKph();
        this.harshAccelerationCount = metrics.harshAccelerationCount();
        this.harshBrakingCount = metrics.harshBrakingCount();
        this.highRpmSeconds = metrics.highRpmSeconds();
        this.drivingScore = metrics.drivingScore();
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public double getDistanceKm() { return distanceKm; }
    public double getAverageSpeedKph() { return averageSpeedKph; }
    public double getMaxSpeedKph() { return maxSpeedKph; }
    public int getHarshAccelerationCount() { return harshAccelerationCount; }
    public int getHarshBrakingCount() { return harshBrakingCount; }
    public long getHighRpmSeconds() { return highRpmSeconds; }
    public int getDrivingScore() { return drivingScore; }
}
