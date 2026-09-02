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
@Table(name = "vehicle_photos")
public class VehiclePhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 40)
    private String mediaType;

    @Column(name = "storage_key", nullable = false, unique = true, length = 100)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width_pixels", nullable = false)
    private int widthPixels;

    @Column(name = "height_pixels", nullable = false)
    private int heightPixels;

    @Column(length = 240)
    private String caption;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VehiclePhoto() {
    }

    public VehiclePhoto(UUID vehicleId, String originalFilename, String mediaType, String storageKey,
                        long sizeBytes, int widthPixels, int heightPixels, String caption, Instant createdAt) {
        this.vehicleId = vehicleId;
        this.originalFilename = originalFilename;
        this.mediaType = mediaType;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.caption = caption == null || caption.isBlank() ? null : caption.trim();
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public String getStorageKey() { return storageKey; }
    public long getSizeBytes() { return sizeBytes; }
    public int getWidthPixels() { return widthPixels; }
    public int getHeightPixels() { return heightPixels; }
    public String getCaption() { return caption; }
    public Instant getCreatedAt() { return createdAt; }
}
