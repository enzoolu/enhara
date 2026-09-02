package br.com.enhara.api.telemetry.domain;

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
@Table(name = "telemetry_samples")
public class TelemetrySample {
    public enum Source { SIMULATED_OBD, SIMULATOR, MOBILE, API }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "speed_kph", nullable = false)
    private double speedKph;

    @Column(nullable = false)
    private int rpm;

    @Column(name = "engine_temp_c", nullable = false)
    private double engineTempC;

    @Column(name = "engine_load_percent", nullable = false)
    private double engineLoadPercent;

    @Column(name = "throttle_position_percent", nullable = false)
    private double throttlePositionPercent;

    @Column(name = "battery_voltage", nullable = false)
    private double batteryVoltage;

    @Column(name = "fuel_level_percent", nullable = false)
    private double fuelLevelPercent;

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    protected TelemetrySample() {
    }

    public TelemetrySample(UUID vehicleId, Instant recordedAt, double speedKph, int rpm,
                           double engineTempC, double engineLoadPercent, double throttlePositionPercent,
                           double batteryVoltage, double fuelLevelPercent,
                           Double latitude, Double longitude, Source source) {
        this.vehicleId = vehicleId;
        this.recordedAt = recordedAt;
        this.speedKph = speedKph;
        this.rpm = rpm;
        this.engineTempC = engineTempC;
        this.engineLoadPercent = engineLoadPercent;
        this.throttlePositionPercent = throttlePositionPercent;
        this.batteryVoltage = batteryVoltage;
        this.fuelLevelPercent = fuelLevelPercent;
        this.latitude = latitude;
        this.longitude = longitude;
        this.source = source;
    }

    public Long getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public Instant getRecordedAt() { return recordedAt; }
    public double getSpeedKph() { return speedKph; }
    public int getRpm() { return rpm; }
    public double getEngineTempC() { return engineTempC; }
    public double getEngineLoadPercent() { return engineLoadPercent; }
    public double getThrottlePositionPercent() { return throttlePositionPercent; }
    public double getBatteryVoltage() { return batteryVoltage; }
    public double getFuelLevelPercent() { return fuelLevelPercent; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Source getSource() { return source; }
}
