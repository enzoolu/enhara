package br.com.enhara.api.vehicle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(unique = true, length = 17)
    private String vin;

    @Column(nullable = false, length = 80)
    private String manufacturer;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "model_year", nullable = false)
    private int year;

    @Column(name = "license_plate", nullable = false, unique = true, length = 10)
    private String licensePlate;

    @Column(name = "odometer_km", nullable = false)
    private double odometerKm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Vehicle() {
    }

    public Vehicle(String name, String vin, String manufacturer, String model, int year, String licensePlate,
                   double odometerKm) {
        this.name = name;
        this.vin = vin == null || vin.isBlank() ? null : vin.toUpperCase();
        this.manufacturer = manufacturer;
        this.model = model;
        this.year = year;
        this.licensePlate = licensePlate.toUpperCase();
        this.odometerKm = odometerKm;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getVin() { return vin; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public String getLicensePlate() { return licensePlate; }
    public double getOdometerKm() { return odometerKm; }
    public Instant getCreatedAt() { return createdAt; }
}
