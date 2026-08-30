package br.com.enhara.api.realtime.api;

import br.com.enhara.api.realtime.application.SseHub;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}")
public class VehicleEventsController {
    private final VehicleService vehicles;
    private final SseHub sseHub;

    public VehicleEventsController(VehicleService vehicles, SseHub sseHub) {
        this.vehicles = vehicles;
        this.sseHub = sseHub;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID vehicleId) {
        vehicles.get(vehicleId);
        return sseHub.subscribe(vehicleId);
    }
}
