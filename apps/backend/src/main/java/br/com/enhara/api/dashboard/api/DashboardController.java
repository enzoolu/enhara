package br.com.enhara.api.dashboard.api;

import br.com.enhara.api.shared.api.ApiModels.AlertResponse;
import br.com.enhara.api.shared.api.ApiModels.DashboardResponse;
import br.com.enhara.api.shared.api.ApiModels.DiagnosticResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryResponse;
import br.com.enhara.api.shared.api.ApiModels.VehicleResponse;
import br.com.enhara.api.simulator.application.SimulationService;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/dashboard")
public class DashboardController {
    private final VehicleService vehicles;
    private final TelemetryService telemetry;
    private final SimulationService simulation;

    public DashboardController(VehicleService vehicles, TelemetryService telemetry, SimulationService simulation) {
        this.vehicles = vehicles;
        this.telemetry = telemetry;
        this.simulation = simulation;
    }

    @GetMapping
    public DashboardResponse dashboard(@PathVariable UUID vehicleId,
                                       @RequestParam(defaultValue = "60") int historyLimit) {
        return new DashboardResponse(VehicleResponse.from(vehicles.get(vehicleId)),
                telemetry.latest(vehicleId).map(TelemetryResponse::from).orElse(null),
                telemetry.history(vehicleId, historyLimit).stream().map(TelemetryResponse::from).toList(),
                telemetry.diagnostics(vehicleId, true).stream().map(DiagnosticResponse::from).toList(),
                telemetry.alerts(vehicleId, true).stream().map(AlertResponse::from).toList(),
                simulation.status(vehicleId).running());
    }
}
