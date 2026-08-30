package br.com.enhara.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enhara.diagnostics")
public record DiagnosticProperties(double engineTemperatureHighCelsius, double batteryVoltageLow,
                                   int engineOverspeedRpm, double fuelLevelLowPercent) {
}
