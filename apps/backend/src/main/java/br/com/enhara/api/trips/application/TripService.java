package br.com.enhara.api.trips.application;

import br.com.enhara.api.realtime.application.SseHub;
import br.com.enhara.api.shared.api.ApiModels.TripResponse;
import br.com.enhara.api.shared.error.ConflictException;
import br.com.enhara.api.telemetry.infrastructure.TelemetryRepository;
import br.com.enhara.api.trips.domain.Trip;
import br.com.enhara.api.trips.domain.TripMetrics;
import br.com.enhara.api.trips.domain.TripMetricsCalculator;
import br.com.enhara.api.trips.infrastructure.TripRepository;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TripService {
    private final VehicleService vehicles;
    private final TripRepository trips;
    private final TelemetryRepository telemetry;
    private final TripMetricsCalculator calculator;
    private final SseHub sseHub;

    public TripService(VehicleService vehicles, TripRepository trips, TelemetryRepository telemetry,
                       TripMetricsCalculator calculator, SseHub sseHub) {
        this.vehicles = vehicles;
        this.trips = trips;
        this.telemetry = telemetry;
        this.calculator = calculator;
        this.sseHub = sseHub;
    }

    @Transactional
    public Trip start(UUID vehicleId) {
        vehicles.get(vehicleId);
        return trips.findFirstByVehicleIdAndEndedAtIsNullOrderByStartedAtDesc(vehicleId)
                .orElseGet(() -> {
                    Trip trip = trips.save(new Trip(vehicleId, databaseTimestamp()));
                    sseHub.publishAfterCommit(vehicleId, "trip-started", TripResponse.from(trip));
                    return trip;
                });
    }

    @Transactional
    public Trip finish(UUID vehicleId) {
        vehicles.get(vehicleId);
        Trip trip = trips.findFirstByVehicleIdAndEndedAtIsNullOrderByStartedAtDesc(vehicleId)
                .orElseThrow(() -> new ConflictException("Nenhuma viagem ativa para este veículo."));
        Instant endedAt = databaseTimestamp();
        TripMetrics metrics = calculator.calculate(telemetry
                .findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(vehicleId, trip.getStartedAt(), endedAt));
        trip.finish(endedAt, metrics);
        Trip saved = trips.save(trip);
        sseHub.publishAfterCommit(vehicleId, "trip-finished", TripResponse.from(saved));
        return saved;
    }

    @Transactional
    public Optional<Trip> finishIfActive(UUID vehicleId) {
        return active(vehicleId).map(ignored -> finish(vehicleId));
    }

    @Transactional(readOnly = true)
    public Optional<Trip> active(UUID vehicleId) {
        vehicles.get(vehicleId);
        return trips.findFirstByVehicleIdAndEndedAtIsNullOrderByStartedAtDesc(vehicleId);
    }

    @Transactional(readOnly = true)
    public List<Trip> history(UUID vehicleId, int limit) {
        vehicles.get(vehicleId);
        return trips.findByVehicleIdOrderByStartedAtDesc(vehicleId,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    private static Instant databaseTimestamp() {
        // PostgreSQL timestamps are stored with microsecond precision. Normalizing the
        // trip boundary prevents a sample recorded at exactly startedAt from falling
        // just outside the query after persistence rounds the instant.
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
