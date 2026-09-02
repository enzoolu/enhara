package br.com.enhara.api.statistics.api;

import br.com.enhara.api.shared.api.ApiModels.VehicleStatisticsResponse;
import br.com.enhara.api.statistics.application.VehicleStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/statistics")
public class VehicleStatisticsController {
    private final VehicleStatisticsService statistics;

    public VehicleStatisticsController(VehicleStatisticsService statistics) {
        this.statistics = statistics;
    }

    @GetMapping
    public VehicleStatisticsResponse get(@PathVariable UUID vehicleId) {
        return statistics.get(vehicleId);
    }
}
