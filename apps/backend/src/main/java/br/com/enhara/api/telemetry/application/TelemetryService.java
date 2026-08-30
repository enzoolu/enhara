package br.com.enhara.api.telemetry.application;

import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.health.application.VehicleHealthService;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.diagnostics.application.DiagnosticRulesService;
import br.com.enhara.api.realtime.application.SseHub;
import br.com.enhara.api.shared.error.ResourceNotFoundException;
import br.com.enhara.api.alerts.infrastructure.AlertRepository;
import br.com.enhara.api.diagnostics.infrastructure.DiagnosticRepository;
import br.com.enhara.api.telemetry.infrastructure.TelemetryRepository;
import br.com.enhara.api.vehicle.application.VehicleService;
import br.com.enhara.api.shared.api.ApiModels.AlertResponse;
import br.com.enhara.api.shared.api.ApiModels.DiagnosticResponse;
import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.shared.api.ApiModels.TelemetryBatchResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TelemetryService {
    private final VehicleService vehicleService;
    private final TelemetryRepository telemetryRepository;
    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;
    private final DiagnosticRulesService rulesService;
    private final SseHub sseHub;
    private final VehicleHealthService healthService;

    public TelemetryService(VehicleService vehicleService, TelemetryRepository telemetryRepository,
                            DiagnosticRepository diagnosticRepository, AlertRepository alertRepository,
                            DiagnosticRulesService rulesService, SseHub sseHub,
                            VehicleHealthService healthService) {
        this.vehicleService = vehicleService;
        this.telemetryRepository = telemetryRepository;
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository = alertRepository;
        this.rulesService = rulesService;
        this.sseHub = sseHub;
        this.healthService = healthService;
    }

    @Transactional
    public IngestionResponse ingest(UUID vehicleId, TelemetryRequest request) {
        vehicleService.get(vehicleId);
        TelemetrySample sample = telemetryRepository.save(new TelemetrySample(vehicleId,
                request.recordedAt() == null ? Instant.now() : request.recordedAt(), request.speedKph(), request.rpm(),
                request.engineTempC(), request.engineLoadPercent(), request.throttlePositionPercent(),
                request.batteryVoltage(), request.fuelLevelPercent(), request.latitude(),
                request.longitude(), request.source() == null ? TelemetrySample.Source.API : request.source()));

        DiagnosticRulesService.Evaluation evaluation = rulesService.evaluate(sample);
        TelemetryResponse telemetry = TelemetryResponse.from(sample);
        List<DiagnosticResponse> diagnostics = evaluation.diagnostics().stream().map(DiagnosticResponse::from).toList();
        List<AlertResponse> alerts = evaluation.alerts().stream().map(AlertResponse::from).toList();
        sseHub.publish(vehicleId, "telemetry", telemetry);
        diagnostics.forEach(item -> sseHub.publish(vehicleId, "diagnostic", item));
        alerts.forEach(item -> sseHub.publish(vehicleId, "alert", item));
        sseHub.publish(vehicleId, "health", healthService.calculate(vehicleId));
        return new IngestionResponse(telemetry, diagnostics, alerts);
    }

    @Transactional
    public TelemetryBatchResponse ingestBatch(UUID vehicleId, List<TelemetryRequest> samples) {
        vehicleService.get(vehicleId);
        List<IngestionResponse> results = samples.stream().map(sample -> ingest(vehicleId, sample)).toList();
        return new TelemetryBatchResponse(vehicleId, results.size(), results);
    }

    @Transactional(readOnly = true)
    public Optional<TelemetrySample> latest(UUID vehicleId) {
        vehicleService.get(vehicleId);
        return telemetryRepository.findTopByVehicleIdOrderByRecordedAtDesc(vehicleId);
    }

    @Transactional(readOnly = true)
    public List<TelemetrySample> history(UUID vehicleId, int limit) {
        vehicleService.get(vehicleId);
        List<TelemetrySample> history = new java.util.ArrayList<>(telemetryRepository
                .findByVehicleIdOrderByRecordedAtDesc(vehicleId, PageRequest.of(0, Math.min(Math.max(limit, 1), 500))));
        Collections.reverse(history);
        return history;
    }

    @Transactional(readOnly = true)
    public List<Diagnostic> diagnostics(UUID vehicleId, boolean activeOnly) {
        vehicleService.get(vehicleId);
        return activeOnly
                ? diagnosticRepository.findByVehicleIdAndStatusOrderByDetectedAtDesc(vehicleId, Diagnostic.Status.ACTIVE)
                : diagnosticRepository.findByVehicleIdOrderByDetectedAtDesc(vehicleId);
    }

    @Transactional(readOnly = true)
    public List<Alert> alerts(UUID vehicleId, boolean openOnly) {
        vehicleService.get(vehicleId);
        return openOnly
                ? alertRepository.findByVehicleIdAndStatusOrderByCreatedAtDesc(vehicleId, Alert.Status.OPEN)
                : alertRepository.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
    }

    @Transactional
    public Alert acknowledgeAlert(UUID vehicleId, UUID alertId) {
        vehicleService.get(vehicleId);
        Alert alert = alertRepository.findById(alertId)
                .filter(item -> item.getVehicleId().equals(vehicleId))
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado: " + alertId));
        if (alert.getStatus() == Alert.Status.OPEN) {
            alert.acknowledge();
            sseHub.publish(vehicleId, "alert-acknowledged", AlertResponse.from(alert));
            sseHub.publish(vehicleId, "health", healthService.calculate(vehicleId));
        }
        return alert;
    }

    @Transactional
    public Alert acknowledgeAlert(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado: " + alertId));
        return acknowledgeAlert(alert.getVehicleId(), alertId);
    }
}
