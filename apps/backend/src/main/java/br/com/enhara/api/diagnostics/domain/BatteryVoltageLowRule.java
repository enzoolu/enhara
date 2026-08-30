package br.com.enhara.api.diagnostics.domain;

import br.com.enhara.api.config.DiagnosticProperties;
import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BatteryVoltageLowRule implements DiagnosticRule {
    private final DiagnosticProperties properties;

    public BatteryVoltageLowRule(DiagnosticProperties properties) {
        this.properties = properties;
    }

    @Override public String code() { return "BATTERY_VOLTAGE_LOW"; }
    @Override public Alert.Type alertType() { return Alert.Type.LOW_BATTERY; }
    @Override public Diagnostic.Severity severity() { return Diagnostic.Severity.WARNING; }
    @Override public String title() { return "Bateria com baixa tensão"; }

    @Override
    public Optional<Finding> evaluate(TelemetrySample sample) {
        if (sample.getBatteryVoltage() >= properties.batteryVoltageLow()) {
            return Optional.empty();
        }
        return Optional.of(new Finding("Tensão do sistema elétrico abaixo da faixa configurada",
                "Valor de %.1f V abaixo da faixa configurada; recomenda-se inspecionar bateria e alternador."
                        .formatted(sample.getBatteryVoltage())));
    }
}
