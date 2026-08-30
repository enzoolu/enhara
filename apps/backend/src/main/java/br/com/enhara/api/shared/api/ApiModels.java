package br.com.enhara.api.shared.api;

import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.trips.domain.Trip;
import br.com.enhara.api.vehicle.domain.Vehicle;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {
    }

    public record CreateVehicleRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}", message = "deve ser um VIN válido com 17 caracteres") String vin,
            @NotBlank @Size(max = 80) String manufacturer,
            @NotBlank @Size(max = 80) String model,
            @Min(1886) @Max(2100) int modelYear,
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{6,10}", message = "deve ser uma placa válida") String licensePlate,
            @PositiveOrZero double odometerKm
    ) {
    }

    public record TelemetryRequest(
            Instant recordedAt,
            @NotNull @DecimalMin("0") @DecimalMax("350") Double speedKph,
            @NotNull @Min(0) @Max(12000) Integer rpm,
            @NotNull @DecimalMin("-50") @DecimalMax("180") Double engineTempC,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double engineLoadPercent,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double throttlePositionPercent,
            @NotNull @DecimalMin("0") @DecimalMax("30") Double batteryVoltage,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double fuelLevelPercent,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            TelemetrySample.Source source
    ) {
    }

    public record VehicleResponse(UUID id, String name, String vin, String manufacturer, String model,
                                  int modelYear, String licensePlate, double odometerKm, Instant createdAt) {
        public static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(vehicle.getId(), vehicle.getName(), vehicle.getVin(), vehicle.getManufacturer(),
                    vehicle.getModel(), vehicle.getYear(), vehicle.getLicensePlate(), vehicle.getOdometerKm(),
                    vehicle.getCreatedAt());
        }
    }

    public record TelemetryResponse(Long id, UUID vehicleId, Instant recordedAt, double speedKph, int rpm,
                                    double engineTempC, double engineLoadPercent, double throttlePositionPercent,
                                    double batteryVoltage, double fuelLevelPercent,
                                    Double latitude, Double longitude, TelemetrySample.Source source) {
        public static TelemetryResponse from(TelemetrySample sample) {
            return new TelemetryResponse(sample.getId(), sample.getVehicleId(), sample.getRecordedAt(),
                    sample.getSpeedKph(), sample.getRpm(), sample.getEngineTempC(), sample.getEngineLoadPercent(),
                    sample.getThrottlePositionPercent(), sample.getBatteryVoltage(), sample.getFuelLevelPercent(),
                    sample.getLatitude(), sample.getLongitude(), sample.getSource());
        }
    }

    public record TelemetryBatchRequest(
            @NotNull UUID vehicleId,
            @NotEmpty @Size(max = 200) List<@Valid TelemetryRequest> samples
    ) {
    }

    public record TelemetryBatchResponse(UUID vehicleId, int acceptedSamples, List<IngestionResponse> results) {
    }

    public record DiagnosticResponse(UUID id, UUID vehicleId, Long telemetryId, String code, String description,
                                     Diagnostic.Severity severity, Diagnostic.Status status,
                                     Instant detectedAt, Instant resolvedAt) {
        public static DiagnosticResponse from(Diagnostic diagnostic) {
            return new DiagnosticResponse(diagnostic.getId(), diagnostic.getVehicleId(), diagnostic.getTelemetryId(),
                    diagnostic.getCode(), diagnostic.getDescription(), diagnostic.getSeverity(), diagnostic.getStatus(),
                    diagnostic.getDetectedAt(), diagnostic.getResolvedAt());
        }
    }

    public record AlertResponse(UUID id, UUID vehicleId, Long telemetryId, Alert.Type type,
                                Diagnostic.Severity severity, String title, String message, Alert.Status status,
                                Instant createdAt, Instant acknowledgedAt) {
        public static AlertResponse from(Alert alert) {
            return new AlertResponse(alert.getId(), alert.getVehicleId(), alert.getTelemetryId(), alert.getType(),
                    alert.getSeverity(), alert.getTitle(), alert.getMessage(), alert.getStatus(),
                    alert.getCreatedAt(), alert.getAcknowledgedAt());
        }
    }

    public record IngestionResponse(TelemetryResponse telemetry, List<DiagnosticResponse> diagnostics,
                                    List<AlertResponse> newAlerts) {
    }

    public record DashboardResponse(VehicleResponse vehicle, TelemetryResponse latestTelemetry,
                                    List<TelemetryResponse> telemetryHistory,
                                    List<DiagnosticResponse> activeDiagnostics,
                                    List<AlertResponse> openAlerts,
                                    boolean simulationRunning,
                                    SimulationScenario simulationScenario,
                                    VehicleHealthResponse health,
                                    TripResponse activeTrip,
                                    List<TripResponse> recentTrips) {
    }

    public enum VehicleHealthStatus { GOOD, ATTENTION, CRITICAL }

    public record VehicleHealthResponse(int score, VehicleHealthStatus status, String label, String explanation,
                                        List<String> observations, String recommendation) {
    }

    public record TripResponse(UUID id, UUID vehicleId, Instant startedAt, Instant endedAt,
                               double distanceKm, double averageSpeedKph, double maxSpeedKph,
                               int harshAccelerationCount, int harshBrakingCount, long highRpmSeconds,
                               int drivingScore, boolean experimentalMetrics) {
        public static TripResponse from(Trip trip) {
            return new TripResponse(trip.getId(), trip.getVehicleId(), trip.getStartedAt(), trip.getEndedAt(),
                    trip.getDistanceKm(), trip.getAverageSpeedKph(), trip.getMaxSpeedKph(),
                    trip.getHarshAccelerationCount(), trip.getHarshBrakingCount(), trip.getHighRpmSeconds(),
                    trip.getDrivingScore(), true);
        }
    }

    public enum SimulationScenario { NORMAL, OVERHEAT, LOW_BATTERY }

    public record SimulationStatus(UUID vehicleId, boolean running, SimulationScenario scenario,
                                   long generatedSamples) {
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path,
                           java.util.Map<String, String> fieldErrors) {
    }
}
