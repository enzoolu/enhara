package br.com.enhara.api.trips.api;

import br.com.enhara.api.shared.api.ApiModels.TripResponse;
import br.com.enhara.api.trips.application.TripService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/trips")
public class TripController {
    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    @GetMapping
    public List<TripResponse> history(@PathVariable UUID vehicleId,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return trips.history(vehicleId, limit).stream().map(TripResponse::from).toList();
    }

    @GetMapping("/active")
    public TripResponse active(@PathVariable UUID vehicleId) {
        return trips.active(vehicleId).map(TripResponse::from).orElse(null);
    }

    @PostMapping("/start")
    public TripResponse start(@PathVariable UUID vehicleId) {
        return TripResponse.from(trips.start(vehicleId));
    }

    @PostMapping("/finish")
    public TripResponse finish(@PathVariable UUID vehicleId) {
        return TripResponse.from(trips.finish(vehicleId));
    }
}
