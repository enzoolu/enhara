package br.com.enhara.api.service;

import br.com.enhara.api.alerts.domain.Alert;
import br.com.enhara.api.alerts.infrastructure.AlertRepository;
import br.com.enhara.api.diagnostics.domain.Diagnostic;
import br.com.enhara.api.diagnostics.infrastructure.DiagnosticRepository;
import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TelemetryServiceIntegrationTest {
    @Autowired VehicleService vehicleService;
    @Autowired TelemetryService telemetryService;
    @Autowired AlertRepository alertRepository;
    @Autowired DiagnosticRepository diagnosticRepository;

    @Test
    void telemetryCreatesAndDeduplicatesCriticalAlertAndDiagnostic() {
        var vehicle = vehicleService.create(new CreateVehicleRequest("Carro teste", "8AGZZZ377VT004251",
                "Chevrolet", "Onix", 2023, "TST1A01", 12_000));
        var request = new TelemetryRequest(Instant.now(), 60.0, 2600, 109.0, 55.0, 35.0, 13.9, 42.0,
                -23.5, -46.6, TelemetrySample.Source.API);

        IngestionResponse first = telemetryService.ingest(vehicle.getId(), request);
        IngestionResponse second = telemetryService.ingest(vehicle.getId(), request);

        assertThat(first.newAlerts()).singleElement().satisfies(alert -> {
            assertThat(alert.type()).isEqualTo(Alert.Type.ENGINE_OVERHEAT);
            assertThat(alert.severity()).isEqualTo(Diagnostic.Severity.CRITICAL);
        });
        assertThat(first.diagnostics()).singleElement().extracting(item -> item.code()).isEqualTo("ENGINE_TEMPERATURE_HIGH");
        assertThat(second.newAlerts()).isEmpty();
        assertThat(second.diagnostics()).isEmpty();
        assertThat(alertRepository.findByVehicleIdAndStatusOrderByCreatedAtDesc(vehicle.getId(), Alert.Status.OPEN))
                .hasSize(1);
        assertThat(diagnosticRepository.findByVehicleIdAndStatusOrderByDetectedAtDesc(vehicle.getId(), Diagnostic.Status.ACTIVE))
                .hasSize(1);
    }

    @Test
    void normalTelemetryResolvesActiveDiagnostic() {
        var vehicle = vehicleService.create(new CreateVehicleRequest("Carro dois", "8AGZZZ377VT004252",
                "Fiat", "Pulse", 2024, "TST1A02", 4_500));
        telemetryService.ingest(vehicle.getId(), new TelemetryRequest(null, 30.0, 1800, 110.0, 40.0, 25.0, 13.8,
                70.0, null, null, TelemetrySample.Source.MOBILE));

        telemetryService.ingest(vehicle.getId(), new TelemetryRequest(null, 30.0, 1800, 90.0, 40.0, 25.0, 13.8,
                70.0, null, null, TelemetrySample.Source.MOBILE));

        assertThat(telemetryService.diagnostics(vehicle.getId(), true)).isEmpty();
        assertThat(telemetryService.diagnostics(vehicle.getId(), false)).singleElement()
                .extracting(Diagnostic::getStatus).isEqualTo(Diagnostic.Status.RESOLVED);
    }


    @Test
    void lowBatteryCreatesWarningAndBatchPersistsEverySample() {
        var vehicle = vehicleService.create(new CreateVehicleRequest("Carro bateria", "8AGZZZ377VT004253",
                "Demo", "Battery", 2025, "TST1A03", 100));
        var lowBattery = new TelemetryRequest(null, 10.0, 1000, 88.0, 25.0, 12.0, 11.4,
                80.0, null, null, TelemetrySample.Source.MOBILE);
        var normal = new TelemetryRequest(null, 15.0, 1200, 89.0, 30.0, 14.0, 13.8,
                79.0, null, null, TelemetrySample.Source.MOBILE);

        var batch = telemetryService.ingestBatch(vehicle.getId(), java.util.List.of(lowBattery, normal));

        assertThat(batch.acceptedSamples()).isEqualTo(2);
        assertThat(telemetryService.history(vehicle.getId(), 10)).hasSize(2);
        assertThat(telemetryService.alerts(vehicle.getId(), true)).singleElement()
                .extracting(Alert::getType).isEqualTo(Alert.Type.LOW_BATTERY);
    }
}
