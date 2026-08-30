package br.com.enhara.api.telemetry.api;

import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.shared.api.ApiModels.TelemetryBatchRequest;
import br.com.enhara.api.shared.api.ApiModels.TelemetryBatchResponse;
import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.shared.api.ApiModels.TelemetryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TelemetryController {
    private final TelemetryService telemetry;

    public TelemetryController(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @PostMapping("/telemetry/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public TelemetryBatchResponse ingestBatch(@Valid @RequestBody TelemetryBatchRequest request) {
        return telemetry.ingestBatch(request.vehicleId(), request.samples());
    }

    @PostMapping("/vehicles/{vehicleId}/telemetry")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestionResponse ingest(@PathVariable UUID vehicleId, @Valid @RequestBody TelemetryRequest request) {
        return telemetry.ingest(vehicleId, request);
    }

    @GetMapping("/vehicles/{vehicleId}/telemetry/latest")
    public ResponseEntity<TelemetryResponse> latest(@PathVariable UUID vehicleId) {
        return ResponseEntity.of(telemetry.latest(vehicleId).map(TelemetryResponse::from));
    }

    @GetMapping("/vehicles/{vehicleId}/telemetry/history")
    public List<TelemetryResponse> history(@PathVariable UUID vehicleId,
                                           @RequestParam(defaultValue = "60") int limit) {
        return telemetry.history(vehicleId, limit).stream().map(TelemetryResponse::from).toList();
    }
}
