package br.com.enhara.api.diagnostics.domain;

import br.com.enhara.api.config.DiagnosticProperties;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticRulesTest {
    private final DiagnosticProperties properties = new DiagnosticProperties(105, 12.0, 6000, 15);

    @Test
    void engineTemperatureHighTriggersOnlyAtConfiguredThreshold() {
        var rule = new EngineTemperatureHighRule(properties);

        assertThat(rule.evaluate(sample(104.9, 13.8))).isEmpty();
        assertThat(rule.evaluate(sample(105.0, 13.8))).isPresent()
                .get().extracting(DiagnosticRule.Finding::description)
                .asString().contains("faixa configurada");
    }

    @Test
    void batteryVoltageLowTriggersBelowConfiguredThreshold() {
        var rule = new BatteryVoltageLowRule(properties);

        assertThat(rule.evaluate(sample(90, 12.0))).isEmpty();
        assertThat(rule.evaluate(sample(90, 11.9))).isPresent()
                .get().extracting(DiagnosticRule.Finding::userMessage)
                .asString().contains("faixa configurada");
    }

    private TelemetrySample sample(double temperature, double voltage) {
        return new TelemetrySample(UUID.randomUUID(), Instant.now(), 50, 2200, temperature,
                45, 30, voltage, 60, null, null, TelemetrySample.Source.API);
    }
}
