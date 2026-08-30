package br.com.enhara.api.diagnostics.domain;

import br.com.enhara.api.config.DiagnosticProperties;
import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EngineTemperatureHighRule implements DiagnosticRule {
    private final DiagnosticProperties properties;

    public EngineTemperatureHighRule(DiagnosticProperties properties) {
        this.properties = properties;
    }

    @Override public String code() { return "ENGINE_TEMPERATURE_HIGH"; }
    @Override public Alert.Type alertType() { return Alert.Type.ENGINE_OVERHEAT; }
    @Override public Diagnostic.Severity severity() { return Diagnostic.Severity.CRITICAL; }
    @Override public String title() { return "Temperatura do motor elevada"; }

    @Override
    public Optional<Finding> evaluate(TelemetrySample sample) {
        if (sample.getEngineTempC() < properties.engineTemperatureHighCelsius()) {
            return Optional.empty();
        }
        return Optional.of(new Finding("Temperatura do motor acima da faixa configurada",
                "Valor de %.1f °C fora da faixa configurada; recomenda-se reduzir a carga e verificar o arrefecimento."
                        .formatted(sample.getEngineTempC())));
    }
}
