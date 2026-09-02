package br.com.enhara.api.simulator.application;

import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.SimulationStatus;
import br.com.enhara.api.shared.api.ApiModels.TelemetryBatchResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.shared.error.ConflictException;
import br.com.enhara.api.simulator.domain.SimulationScenario;
import br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId;
import br.com.enhara.api.simulator.domain.StatefulVehicleSimulator;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.trips.application.TripService;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationService {
    private final VehicleService vehicleService;
    private final TelemetryService telemetryService;
    private final TripService tripService;
    private final ConcurrentHashMap<UUID, RuntimeState> states = new ConcurrentHashMap<>();

    public SimulationService(VehicleService vehicleService, TelemetryService telemetryService, TripService tripService) {
        this.vehicleService = vehicleService;
        this.telemetryService = telemetryService;
        this.tripService = tripService;
    }

    public SimulationStatus start(UUID vehicleId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        tripService.start(vehicleId);
        boolean generateInitial;
        synchronized (state) {
            state.running = true;
            state.externalSnapshot = null;
            state.externalSnapshotReceivedAt = null;
            generateInitial = state.generatedSamples == 0;
        }
        if (generateInitial) tick(vehicleId);
        return status(vehicleId);
    }

    public SimulationStatus stop(UUID vehicleId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            state.running = false;
        }
        tripService.finishIfActive(vehicleId);
        return status(vehicleId);
    }

    public SimulationStatus status(UUID vehicleId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            return new SimulationStatus(vehicleId, state.running, state.simulator.scenario(),
                    state.generatedSamples, state.simulator.profileId());
        }
    }

    public StatefulVehicleSimulator.Snapshot obdState(UUID vehicleId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            Instant now = Instant.now();
            return state.externalSnapshot == null
                    ? StatefulVehicleSimulator.refreshAvailability(state.publishedLocalSnapshot, now)
                    : StatefulVehicleSimulator.refreshAvailability(state.externalSnapshot, now);
        }
    }

    public TelemetryBatchResponse ingestExternalBatch(UUID vehicleId, List<TelemetryRequest> samples,
                                                      StatefulVehicleSimulator.Snapshot snapshot) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            if (state.running) {
                throw new ConflictException("Pare a simulação do backend antes de enviar o estado OBD externo");
            }
            if (snapshot.liveData().isEmpty()) {
                throw new IllegalArgumentException("O snapshot OBD enviado com telemetria deve conter live data");
            }
            StatefulVehicleSimulator.Snapshot previousSnapshot = state.externalSnapshot;
            Instant previousReceivedAt = state.externalSnapshotReceivedAt;
            state.externalSnapshot = snapshot;
            state.externalSnapshotReceivedAt = Instant.now();
            try {
                // Readers use this same lock, so the snapshot only becomes observable
                // after the batch transaction has returned successfully.
                return telemetryService.ingestBatch(vehicleId, samples);
            } catch (RuntimeException exception) {
                state.externalSnapshot = previousSnapshot;
                state.externalSnapshotReceivedAt = previousReceivedAt;
                throw exception;
            }
        }
    }

    public boolean isVehicleDataConnected(UUID vehicleId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            return state.running || state.externalSnapshotReceivedAt != null
                    && !state.externalSnapshotReceivedAt.plus(StatefulVehicleSimulator.LIVE_DATA_STALE_AFTER)
                    .isBefore(Instant.now());
        }
    }

    public SimulationStatus setScenario(UUID vehicleId, SimulationScenario scenario) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            state.simulator.setScenario(scenario);
            state.publishedLocalSnapshot = state.simulator.snapshot(Instant.now());
        }
        return status(vehicleId);
    }

    public SimulationStatus setProfile(UUID vehicleId, ProfileId profileId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        synchronized (state) {
            state.simulator = new StatefulVehicleSimulator(profileId);
            state.publishedLocalSnapshot = state.simulator.snapshot(Instant.now());
            state.generatedSamples = 0;
            state.externalSnapshot = null;
            state.externalSnapshotReceivedAt = null;
        }
        return status(vehicleId);
    }

    public IngestionResponse tick(UUID vehicleId) {
        vehicleService.get(vehicleId);
        RuntimeState state = state(vehicleId);
        Instant observedAt = Instant.now();
        synchronized (state) {
            StatefulVehicleSimulator.Frame frame = state.simulator.tick(observedAt);
            IngestionResponse result = telemetryService.ingest(vehicleId,
                    new TelemetryRequest(observedAt, frame.speedKph(), frame.rpm(), frame.engineTempC(),
                            frame.engineLoadPercent(), frame.throttlePositionPercent(),
                            frame.controlModuleVoltage(), frame.fuelLevelPercent(), null, null,
                            TelemetrySample.Source.SIMULATED_OBD));
            // Readers use this same lock. The new ECU state only becomes observable
            // after the telemetry transaction has committed successfully.
            state.publishedLocalSnapshot = frame.snapshot();
            state.externalSnapshot = null;
            state.externalSnapshotReceivedAt = null;
            state.generatedSamples++;
            return result;
        }
    }

    @Scheduled(fixedRate = 2_000)
    void generateTelemetry() {
        states.forEach((vehicleId, state) -> {
            boolean running;
            synchronized (state) {
                running = state.running;
            }
            if (running) {
                try {
                    tick(vehicleId);
                } catch (RuntimeException exception) {
                    synchronized (state) {
                        state.running = false;
                    }
                }
            }
        });
    }

    private RuntimeState state(UUID vehicleId) {
        return states.computeIfAbsent(vehicleId, ignored -> new RuntimeState());
    }

    private static final class RuntimeState {
        private boolean running;
        private long generatedSamples;
        private StatefulVehicleSimulator simulator = new StatefulVehicleSimulator(ProfileId.COMPACT_GASOLINE);
        private StatefulVehicleSimulator.Snapshot publishedLocalSnapshot = simulator.snapshot();
        private StatefulVehicleSimulator.Snapshot externalSnapshot;
        private Instant externalSnapshotReceivedAt;
    }
}
