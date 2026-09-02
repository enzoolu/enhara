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
@Table(name = "vehicle_profile_fields")
public class VehicleProfileField {
    public enum Key {
        VIN, MANUFACTURER, MODEL, MODEL_YEAR, VERSION, ENGINE, FUEL_TYPE, TRANSMISSION,
        FIPE_CODE, FIPE_VALUE, FIPE_REFERENCE_MONTH
    }

    public enum Source { VEHICLE_REGISTRATION, ECU_OBD, BRASILAPI_FIPE, NHTSA_VPIC, USER_PROVIDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_key", nullable = false, length = 40)
    private Key key;

    @Column(name = "field_value", nullable = false, length = 512)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Source source;

    @Column(length = 32)
    private String provider;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "provider_expires_at")
    private Instant providerExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected VehicleProfileField() {
    }

    public VehicleProfileField(UUID vehicleId, Key key, String value, Source source, String provider,
                               String sourceUrl, Instant observedAt, Instant retrievedAt,
                               Instant providerExpiresAt, Instant confirmedAt) {
        this.vehicleId = vehicleId;
        this.key = key;
        replace(value, source, provider, sourceUrl, observedAt, retrievedAt, providerExpiresAt, confirmedAt);
    }

    public void replace(String value, Source source, String provider, String sourceUrl, Instant observedAt,
                        Instant retrievedAt, Instant providerExpiresAt, Instant confirmedAt) {
        this.value = value.trim();
        this.source = source;
        this.provider = provider;
        this.sourceUrl = sourceUrl;
        this.observedAt = observedAt;
        this.retrievedAt = retrievedAt;
        this.providerExpiresAt = providerExpiresAt;
        this.confirmedAt = confirmedAt;
    }

    public void confirm(Instant now) {
        this.confirmedAt = now;
    }

    public boolean isUserControlled() {
        return source == Source.USER_PROVIDED || confirmedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public Key getKey() { return key; }
    public String getValue() { return value; }
    public Source getSource() { return source; }
    public String getProvider() { return provider; }
    public String getSourceUrl() { return sourceUrl; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public Instant getProviderExpiresAt() { return providerExpiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
