package br.com.enhara.api.simulator.api;

import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.SimulationStatus;
import br.com.enhara.api.simulator.application.SimulationService;
import br.com.enhara.api.simulator.domain.SimulationScenario;
import br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId;
import br.com.enhara.api.simulator.domain.StatefulVehicleSimulator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/simulation")
public class SimulationController {
    private final SimulationService simulation;

    public SimulationController(SimulationService simulation) {
        this.simulation = simulation;
    }

    @GetMapping
    public SimulationStatus status(@PathVariable UUID vehicleId) {
        return simulation.status(vehicleId);
    }

    @PostMapping("/start")
    public SimulationStatus start(@PathVariable UUID vehicleId) {
        return simulation.start(vehicleId);
    }

    @PostMapping("/stop")
    public SimulationStatus stop(@PathVariable UUID vehicleId) {
        return simulation.stop(vehicleId);
    }

    @PostMapping("/tick")
    public IngestionResponse tick(@PathVariable UUID vehicleId) {
        return simulation.tick(vehicleId);
    }

    @PostMapping("/scenario/{scenario}")
    public SimulationStatus scenario(@PathVariable UUID vehicleId, @PathVariable SimulationScenario scenario) {
        return simulation.setScenario(vehicleId, scenario);
    }

    @PostMapping("/profile/{profile}")
    public SimulationStatus profile(@PathVariable UUID vehicleId, @PathVariable ProfileId profile) {
        return simulation.setProfile(vehicleId, profile);
    }

    @GetMapping("/obd")
    public StatefulVehicleSimulator.Snapshot obdState(@PathVariable UUID vehicleId) {
        return simulation.obdState(vehicleId);
    }
}
