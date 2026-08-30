package br.com.enhara.api.diagnostics.domain;

import br.com.enhara.api.config.DiagnosticProperties;
import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EngineOverspeedRule implements DiagnosticRule {
    private final DiagnosticProperties properties;

    public EngineOverspeedRule(DiagnosticProperties properties) {
        this.properties = properties;
    }

    @Override public String code() { return "ENGINE_OVERSPEED"; }
    @Override public Alert.Type alertType() { return Alert.Type.ENGINE_OVERSPEED; }
    @Override public Diagnostic.Severity severity() { return Diagnostic.Severity.WARNING; }
    @Override public String title() { return "Rotação excessiva"; }

    @Override
    public Optional<Finding> evaluate(TelemetrySample sample) {
        if (sample.getRpm() < properties.engineOverspeedRpm()) {
            return Optional.empty();
        }
        return Optional.of(new Finding("Rotação do motor acima da faixa configurada",
                "Rotação de %d rpm fora da faixa configurada; recomenda-se reduzir o giro."
                        .formatted(sample.getRpm())));
    }
}
