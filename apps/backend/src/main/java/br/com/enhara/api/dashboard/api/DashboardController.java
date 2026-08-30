package br.com.enhara.api.dashboard.api;

import br.com.enhara.api.shared.api.ApiModels.AlertResponse;
import br.com.enhara.api.shared.api.ApiModels.DashboardResponse;
import br.com.enhara.api.shared.api.ApiModels.DiagnosticResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryResponse;
import br.com.enhara.api.shared.api.ApiModels.VehicleResponse;
import br.com.enhara.api.simulator.application.SimulationService;
import br.com.enhara.api.health.application.VehicleHealthService;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.trips.application.TripService;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/dashboard")
public class DashboardController {
    private final VehicleService vehicles;
    private final TelemetryService telemetry;
    private final SimulationService simulation;
    private final VehicleHealthService health;
    private final TripService trips;

    public DashboardController(VehicleService vehicles, TelemetryService telemetry, SimulationService simulation,
                               VehicleHealthService health, TripService trips) {
        this.vehicles = vehicles;
        this.telemetry = telemetry;
        this.simulation = simulation;
        this.health = health;
        this.trips = trips;
    }

    @GetMapping
    public DashboardResponse dashboard(@PathVariable UUID vehicleId,
                                       @RequestParam(defaultValue = "60") @Min(1) @Max(500) int historyLimit) {
        var simulationStatus = simulation.status(vehicleId);
        return new DashboardResponse(VehicleResponse.from(vehicles.get(vehicleId)),
                telemetry.latest(vehicleId).map(TelemetryResponse::from).orElse(null),
                telemetry.history(vehicleId, historyLimit).stream().map(TelemetryResponse::from).toList(),
                telemetry.diagnostics(vehicleId, true).stream().map(DiagnosticResponse::from).toList(),
                telemetry.alerts(vehicleId, true).stream().map(AlertResponse::from).toList(),
                simulationStatus.running(), simulationStatus.scenario(), health.calculate(vehicleId),
                trips.active(vehicleId).map(br.com.enhara.api.shared.api.ApiModels.TripResponse::from).orElse(null),
                trips.history(vehicleId, 8).stream().map(br.com.enhara.api.shared.api.ApiModels.TripResponse::from).toList());
    }
}
