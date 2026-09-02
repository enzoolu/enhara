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
@Table(name = "vehicle_provider_cache")
public class VehicleProviderCacheEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "lookup_key", nullable = false, length = 128)
    private String lookupKey;

    @Column(name = "payload_json", nullable = false, length = 8000)
    private String payloadJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected VehicleProviderCacheEntry() {
    }

    public VehicleProviderCacheEntry(String provider, String lookupKey, String payloadJson,
                                     Instant fetchedAt, Instant expiresAt) {
        this.provider = provider;
        this.lookupKey = lookupKey;
        update(payloadJson, fetchedAt, expiresAt);
    }

    public void update(String payloadJson, Instant fetchedAt, Instant expiresAt) {
        this.payloadJson = payloadJson;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getLookupKey() { return lookupKey; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
