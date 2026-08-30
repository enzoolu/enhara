package br.com.enhara.api.diagnostics.api;

import br.com.enhara.api.shared.api.ApiModels.DiagnosticResponse;
import br.com.enhara.api.telemetry.application.TelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/diagnostics")
public class DiagnosticController {
    private final TelemetryService telemetry;

    public DiagnosticController(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @GetMapping
    public List<DiagnosticResponse> list(@PathVariable UUID vehicleId,
                                         @RequestParam(defaultValue = "false") boolean activeOnly) {
        return telemetry.diagnostics(vehicleId, activeOnly).stream().map(DiagnosticResponse::from).toList();
    }
}
