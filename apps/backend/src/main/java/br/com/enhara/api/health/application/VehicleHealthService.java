package br.com.enhara.api.health.application;

import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.alerts.infrastructure.AlertRepository;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.diagnostics.infrastructure.DiagnosticRepository;
import br.com.enhara.api.shared.api.ApiModels.VehicleHealthResponse;
import br.com.enhara.api.shared.api.ApiModels.VehicleHealthStatus;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.telemetry.infrastructure.TelemetryRepository;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleHealthService {
    private final VehicleService vehicles;
    private final TelemetryRepository telemetry;
    private final DiagnosticRepository diagnostics;
    private final AlertRepository alerts;

    public VehicleHealthService(VehicleService vehicles, TelemetryRepository telemetry,
                                DiagnosticRepository diagnostics, AlertRepository alerts) {
        this.vehicles = vehicles;
        this.telemetry = telemetry;
        this.diagnostics = diagnostics;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public VehicleHealthResponse calculate(UUID vehicleId) {
        vehicles.get(vehicleId);
        TelemetrySample latest = telemetry.findTopByVehicleIdOrderByRecordedAtDesc(vehicleId).orElse(null);
        List<Diagnostic> activeDiagnostics = diagnostics
                .findByVehicleIdAndStatusOrderByDetectedAtDesc(vehicleId, Diagnostic.Status.ACTIVE);
        List<Alert> openAlerts = alerts.findByVehicleIdAndStatusOrderByCreatedAtDesc(vehicleId, Alert.Status.OPEN);
        int score = 100;
        List<String> observations = new ArrayList<>();

        if (latest == null) {
            return new VehicleHealthResponse(0, VehicleHealthStatus.ATTENTION, "Atenção necessária",
                    "Ainda não há telemetria suficiente para avaliar o veículo.", List.of("Nenhuma leitura recebida."),
                    "Inicie a simulação ou conecte uma fonte de dados para obter o indicador.");
        }

        if (latest.getEngineTempC() >= 105) {
            score -= 45;
            observations.add("Temperatura do motor observada acima de 105 °C.");
        } else if (latest.getEngineTempC() >= 100) {
            score -= 25;
            observations.add("Temperatura do motor observada acima de 100 °C.");
        } else if (latest.getEngineTempC() >= 96) {
            score -= 10;
            observations.add("Temperatura do motor próxima da faixa de atenção.");
        }

        if (latest.getBatteryVoltage() < 11.8) {
            score -= 35;
            observations.add("Tensão da bateria observada abaixo de 11,8 V.");
        } else if (latest.getBatteryVoltage() < 12.4) {
            score -= 20;
            observations.add("Tensão da bateria observada abaixo de 12,4 V.");
        } else if (latest.getBatteryVoltage() < 13.0) {
            score -= 8;
            observations.add("Tensão da bateria em faixa de atenção.");
        }

        score -= activeDiagnostics.stream().mapToInt(this::diagnosticPenalty).sum();
        score -= openAlerts.stream().mapToInt(this::alertPenalty).sum();
        if (!activeDiagnostics.isEmpty()) {
            observations.add(activeDiagnostics.size() + " diagnóstico(s) ativo(s) detectado(s) por regras determinísticas.");
        }
        if (!openAlerts.isEmpty()) {
            observations.add(openAlerts.size() + " alerta(s) aberto(s) requer(em) revisão.");
        }
        if (observations.isEmpty()) {
            observations.add("Temperatura, bateria e diagnósticos sem anomalias nas leituras atuais.");
        }

        score = Math.max(0, Math.min(100, score));
        VehicleHealthStatus status = score < 50 ? VehicleHealthStatus.CRITICAL
                : score < 80 ? VehicleHealthStatus.ATTENTION : VehicleHealthStatus.GOOD;
        return switch (status) {
            case GOOD -> new VehicleHealthResponse(score, status, "Veículo saudável",
                    "Os dados observados estão dentro das faixas esperadas do MVP.", observations,
                    "Continue acompanhando as próximas leituras.");
            case ATTENTION -> new VehicleHealthResponse(score, status, "Atenção necessária",
                    "Uma ou mais leituras merecem acompanhamento.", observations,
                    "Revise os alertas e acompanhe a evolução das leituras.");
            case CRITICAL -> new VehicleHealthResponse(score, status, "Situação crítica",
                    "As regras do MVP identificaram uma condição de alta prioridade.", observations,
                    "Pare em local seguro e procure avaliação profissional se a condição persistir.");
        };
    }

    private int diagnosticPenalty(Diagnostic diagnostic) {
        return switch (diagnostic.getSeverity()) {
            case INFO -> 4;
            case WARNING -> 12;
            case CRITICAL -> 30;
        };
    }

    private int alertPenalty(Alert alert) {
        return switch (alert.getSeverity()) {
            case INFO -> 3;
            case WARNING -> 8;
            case CRITICAL -> 20;
        };
    }
}
