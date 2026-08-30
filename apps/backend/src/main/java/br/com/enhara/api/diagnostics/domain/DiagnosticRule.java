package br.com.enhara.api.diagnostics.domain;

import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.telemetry.domain.TelemetrySample;

import java.util.Optional;

public interface DiagnosticRule {
    String code();
    Alert.Type alertType();
    Diagnostic.Severity severity();
    String title();
    Optional<Finding> evaluate(TelemetrySample sample);

    record Finding(String description, String userMessage) {
    }
}
