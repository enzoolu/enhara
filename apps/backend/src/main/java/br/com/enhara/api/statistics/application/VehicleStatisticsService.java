package br.com.enhara.api.statistics.application;

import br.com.enhara.api.shared.api.ApiModels.ConsumptionAvailability;
import br.com.enhara.api.shared.api.ApiModels.VehicleStatisticsResponse;
import br.com.enhara.api.telemetry.infrastructure.TelemetryRepository;
import br.com.enhara.api.trips.infrastructure.TripRepository;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VehicleStatisticsService {
    private final VehicleService vehicles;
    private final TelemetryRepository telemetry;
    private final TripRepository trips;

    public VehicleStatisticsService(VehicleService vehicles, TelemetryRepository telemetry, TripRepository trips) {
        this.vehicles = vehicles;
        this.telemetry = telemetry;
        this.trips = trips;
    }

    @Transactional(readOnly = true)
    public VehicleStatisticsResponse get(UUID vehicleId) {
        vehicles.get(vehicleId);
        double distanceTrackedKm = valueOrZero(trips.sumCompletedDistanceKm(vehicleId));
        Double maxRecordedSpeedKph = telemetry.findMaxRecordedSpeedKph(vehicleId);

        // The current telemetry contract does not carry fuel rate or a validated consumed-fuel series.
        // Fuel-level snapshots alone are not sufficient to infer consumption safely.
        return new VehicleStatisticsResponse(distanceTrackedKm, maxRecordedSpeedKph, null,
                ConsumptionAvailability.INSUFFICIENT_DATA,
                trips.countByVehicleIdAndEndedAtIsNotNull(vehicleId));
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }
}
