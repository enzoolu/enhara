package br.com.enhara.api.health.api;

import br.com.enhara.api.health.application.VehicleHealthService;
import br.com.enhara.api.shared.api.ApiModels.VehicleHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/health")
public class VehicleHealthController {
    private final VehicleHealthService health;

    public VehicleHealthController(VehicleHealthService health) {
        this.health = health;
    }

    @GetMapping
    public VehicleHealthResponse health(@PathVariable UUID vehicleId) {
        return health.calculate(vehicleId);
    }
}
