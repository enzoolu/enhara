package br.com.enhara.api.vehicle.api;

import br.com.enhara.api.vehicle.application.provider.FipeCatalogProvider;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import br.com.enhara.api.vehicle.domain.VehicleProviderStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VehicleProfileModels {
    private VehicleProfileModels() {
    }

    public record ProvenanceResponse(VehicleProfileField.Source source, String provider, String sourceUrl,
                                     Instant observedAt, Instant retrievedAt, Instant cacheExpiresAt,
                                     boolean cached, boolean stale, Instant confirmedAt) {
    }

    public record ProfileFieldResponse(VehicleProfileField.Key key, String value, ProvenanceResponse provenance) {
    }

    public record ProviderStatusResponse(String provider, VehicleProviderStatus.State state, String message,
                                         Instant checkedAt, Instant dataFetchedAt) {
        public static ProviderStatusResponse from(VehicleProviderStatus status) {
            return new ProviderStatusResponse(status.getProvider(), status.getState(), status.getMessage(),
                    status.getCheckedAt(), status.getDataFetchedAt());
        }
    }

    public record VehicleProfileResponse(UUID vehicleId, List<ProfileFieldResponse> fields,
                                         List<ProviderStatusResponse> providers, Instant updatedAt) {
    }

    public record ManualProfileRequest(
            @NotEmpty Map<VehicleProfileField.Key, @NotNull @Size(min = 1, max = 512) String> fields) {
    }

    public record ConfirmProfileRequest(@NotEmpty Set<VehicleProfileField.Key> fields) {
    }

    public record FipeSelectionRequest(
            @NotNull FipeCatalogProvider.VehicleType vehicleType,
            @NotNull @Pattern(regexp = "^[0-9]+$") String brandCode,
            @NotNull @Pattern(regexp = "^[0-9]+$") String modelCode,
            @NotNull @Pattern(regexp = "^[0-9]{4,5}-[0-9]+$") String yearCode) {
    }

    public record EnrichProfileRequest(
            @Pattern(regexp = "^$|^[0-9]{6}-[0-9]$", message = "deve usar o formato FIPE 000000-0")
            String fipeCode,
            @Valid FipeSelectionRequest fipeSelection,
            boolean forceRefresh) {
    }

    public record FipeOptionResponse(String code, String label) {
        public static FipeOptionResponse from(FipeCatalogProvider.Option option) {
            return new FipeOptionResponse(option.code(), option.label());
        }
    }

    public enum EcuAcquisitionSource { REAL_OBD }

    public record EcuVinRequest(
            @NotNull @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$", message = "deve ser um VIN válido com 17 caracteres")
            String vin,
            Instant observedAt,
            @NotNull EcuAcquisitionSource source) {
    }

    public record VehiclePhotoResponse(UUID id, UUID vehicleId, String originalFilename, String mediaType,
                                       long sizeBytes, int widthPixels, int heightPixels, String caption,
                                       Instant createdAt, VehicleProfileField.Source source, String contentPath) {
    }
}
