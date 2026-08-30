package br.com.enhara.api.simulator.application;

import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.vehicle.application.VehicleService;
import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.SimulationScenario;
import br.com.enhara.api.shared.api.ApiModels.SimulationStatus;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.trips.application.TripService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SimulationService {
    private final VehicleService vehicleService;
    private final TelemetryService telemetryService;
    private final TripService tripService;
    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    public SimulationService(VehicleService vehicleService, TelemetryService telemetryService, TripService tripService) {
        this.vehicleService = vehicleService;
        this.telemetryService = telemetryService;
        this.tripService = tripService;
    }

    public SimulationStatus start(UUID vehicleId) {
        vehicleService.get(vehicleId);
        State state = states.computeIfAbsent(vehicleId, ignored -> new State());
        tripService.start(vehicleId);
        state.running.set(true);
        if (state.generated.get() == 0) {
            tick(vehicleId);
        }
        return status(vehicleId);
    }

    public SimulationStatus stop(UUID vehicleId) {
        vehicleService.get(vehicleId);
        states.computeIfAbsent(vehicleId, ignored -> new State()).running.set(false);
        tripService.finishIfActive(vehicleId);
        return status(vehicleId);
    }

    public SimulationStatus status(UUID vehicleId) {
        vehicleService.get(vehicleId);
        State state = states.get(vehicleId);
        return new SimulationStatus(vehicleId, state != null && state.running.get(),
                state == null ? SimulationScenario.NORMAL : state.scenario.get(),
                state == null ? 0 : state.generated.get());
    }

    public SimulationStatus setScenario(UUID vehicleId, SimulationScenario scenario) {
        vehicleService.get(vehicleId);
        State state = states.computeIfAbsent(vehicleId, ignored -> new State());
        state.scenario.set(scenario);
        state.scenarioTicks.set(0);
        return status(vehicleId);
    }

    public IngestionResponse tick(UUID vehicleId) {
        vehicleService.get(vehicleId);
        State state = states.computeIfAbsent(vehicleId, ignored -> new State());
        long sequence = state.generated.incrementAndGet();
        long scenarioTick = state.scenarioTicks.getAndIncrement();
        double speed = Math.max(0, 52 + Math.sin(sequence / 2.2) * 37);
        int rpm = (int) Math.min(4_700, 850 + speed * 40);
        double engineLoad = Math.min(92, 20 + speed * 0.62);
        double throttle = Math.min(85, 8 + speed * 0.55);
        double temperature = state.scenario.get() == SimulationScenario.OVERHEAT
                ? Math.min(119, 90 + scenarioTick * 3.2)
                : 90 + Math.sin(sequence / 3.0) * 4;
        double voltage = state.scenario.get() == SimulationScenario.LOW_BATTERY
                ? Math.max(10.6, 13.8 - scenarioTick * 0.45)
                : 13.8 + Math.sin(sequence) * 0.2;
        double fuel = Math.max(35, 72 - sequence * 0.08);
        double latitude = -23.55052 + Math.sin(sequence / 20.0) * 0.002;
        double longitude = -46.633308 + Math.cos(sequence / 20.0) * 0.002;

        return telemetryService.ingest(vehicleId, new TelemetryRequest(Instant.now(), round(speed), rpm,
                round(temperature), round(engineLoad), round(throttle), round(voltage), round(fuel), latitude,
                longitude, TelemetrySample.Source.SIMULATOR));
    }

    @Scheduled(fixedRate = 2_000)
    void generateTelemetry() {
        states.forEach((vehicleId, state) -> {
            if (state.running.get()) {
                try {
                    tick(vehicleId);
                } catch (RuntimeException exception) {
                    state.running.set(false);
                }
            }
        });
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class State {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicLong generated = new AtomicLong(0);
        private final AtomicLong scenarioTicks = new AtomicLong(0);
        private final AtomicReference<SimulationScenario> scenario = new AtomicReference<>(SimulationScenario.NORMAL);
    }
}
