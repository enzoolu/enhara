package br.com.enhara.api.diagnostics.application;

import br.com.enhara.api.config.DiagnosticProperties;
import br.com.enhara.api.diagnostics.domain.DiagnosticRule;
import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.alerts.infrastructure.AlertRepository;
import br.com.enhara.api.diagnostics.infrastructure.DiagnosticRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DiagnosticRulesService {
    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;
    private final DiagnosticProperties properties;
    private final List<DiagnosticRule> rules;

    public DiagnosticRulesService(DiagnosticRepository diagnosticRepository, AlertRepository alertRepository,
                                  DiagnosticProperties properties, List<DiagnosticRule> rules) {
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository = alertRepository;
        this.properties = properties;
        this.rules = rules;
    }

    public Evaluation evaluate(TelemetrySample sample) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();

        rules.forEach(rule -> rule.evaluate(sample).ifPresentOrElse(
                finding -> activate(sample, rule, finding, diagnostics, alerts),
                () -> resolve(sample, rule.code())));

        if (sample.getFuelLevelPercent() <= properties.fuelLevelLowPercent()) {
            createAlertIfAbsent(sample, Alert.Type.LOW_FUEL, Diagnostic.Severity.WARNING, "Combustível baixo",
                    "Restam %.0f%% no tanque".formatted(sample.getFuelLevelPercent())).ifPresent(alerts::add);
        }

        return new Evaluation(diagnostics, alerts);
    }

    private void activate(TelemetrySample sample, DiagnosticRule rule, DiagnosticRule.Finding finding,
                          List<Diagnostic> newDiagnostics, List<Alert> newAlerts) {
        diagnosticRepository.findFirstByVehicleIdAndCodeAndStatus(sample.getVehicleId(), rule.code(), Diagnostic.Status.ACTIVE)
                .or(() -> java.util.Optional.of(diagnosticRepository.save(new Diagnostic(sample.getVehicleId(),
                        sample.getId(), rule.code(), finding.description(), rule.severity()))))
                .filter(diagnostic -> diagnostic.getTelemetryId().equals(sample.getId()))
                .ifPresent(newDiagnostics::add);
        createAlertIfAbsent(sample, rule.alertType(), rule.severity(), rule.title(), finding.userMessage())
                .ifPresent(newAlerts::add);
    }

    private void resolve(TelemetrySample sample, String code) {
        diagnosticRepository.findFirstByVehicleIdAndCodeAndStatus(sample.getVehicleId(), code, Diagnostic.Status.ACTIVE)
                .ifPresent(Diagnostic::resolve);
    }

    private java.util.Optional<Alert> createAlertIfAbsent(TelemetrySample sample, Alert.Type type,
                                                           Diagnostic.Severity severity, String title, String message) {
        if (alertRepository.findFirstByVehicleIdAndTypeAndStatus(sample.getVehicleId(), type, Alert.Status.OPEN).isPresent()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(alertRepository.save(new Alert(sample.getVehicleId(), sample.getId(), type,
                severity, title, message)));
    }

    public record Evaluation(List<Diagnostic> diagnostics, List<Alert> alerts) {
    }
}
