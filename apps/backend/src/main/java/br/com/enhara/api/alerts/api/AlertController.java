package br.com.enhara.api.alerts.api;

import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.shared.api.ApiModels.AlertResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AlertController {
    private final TelemetryService telemetry;

    public AlertController(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @GetMapping("/vehicles/{vehicleId}/alerts")
    public java.util.List<AlertResponse> list(@PathVariable UUID vehicleId,
                                              @RequestParam(name = "active", defaultValue = "false") boolean activeOnly) {
        return telemetry.alerts(vehicleId, activeOnly).stream().map(AlertResponse::from).toList();
    }

    @PatchMapping("/alerts/{alertId}/acknowledge")
    public AlertResponse acknowledge(@PathVariable UUID alertId) {
        return AlertResponse.from(telemetry.acknowledgeAlert(alertId));
    }

}
