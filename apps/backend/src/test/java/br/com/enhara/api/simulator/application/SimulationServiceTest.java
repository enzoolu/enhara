package br.com.enhara.api.simulator.application;

import br.com.enhara.api.shared.api.ApiModels.IngestionResponse;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.simulator.domain.StatefulVehicleSimulator;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.trips.application.TripService;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId.COMPACT_GASOLINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationServiceTest {
    @Test
    void failedLocalTickKeepsLastPersistedObdSnapshotAndCounter() {
        VehicleService vehicles = mock(VehicleService.class);
        TelemetryService telemetry = mock(TelemetryService.class);
        TripService trips = mock(TripService.class);
        SimulationService service = new SimulationService(vehicles, telemetry, trips);
        UUID vehicleId = UUID.randomUUID();
        when(telemetry.ingest(eq(vehicleId), any()))
                .thenReturn(mock(IngestionResponse.class))
                .thenThrow(new IllegalStateException("database offline"));

        service.tick(vehicleId);
        var persistedSnapshot = service.obdState(vehicleId);

        assertThatThrownBy(() -> service.tick(vehicleId)).isInstanceOf(IllegalStateException.class);

        assertThat(service.status(vehicleId).generatedSamples()).isEqualTo(1);
        assertThat(service.obdState(vehicleId).elapsedSeconds()).isEqualTo(persistedSnapshot.elapsedSeconds());
        assertThat(service.obdState(vehicleId).vehicleState()).isEqualTo(persistedSnapshot.vehicleState());
    }

    @Test
    void failedExternalBatchDoesNotPublishItsObdSnapshot() {
        VehicleService vehicles = mock(VehicleService.class);
        TelemetryService telemetry = mock(TelemetryService.class);
        TripService trips = mock(TripService.class);
        SimulationService service = new SimulationService(vehicles, telemetry, trips);
        UUID vehicleId = UUID.randomUUID();
        StatefulVehicleSimulator ecu = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        var frame = ecu.tick(Instant.parse("2026-09-01T12:00:00Z"));
        List<TelemetryRequest> samples = List.of(new TelemetryRequest(
                frame.snapshot().liveData().getFirst().observedAt(), frame.speedKph(), frame.rpm(),
                frame.engineTempC(), frame.engineLoadPercent(), frame.throttlePositionPercent(),
                frame.controlModuleVoltage(), frame.fuelLevelPercent(), null, null, null));
        when(telemetry.ingestBatch(eq(vehicleId), any())).thenThrow(new IllegalStateException("database offline"));

        assertThatThrownBy(() -> service.ingestExternalBatch(vehicleId, samples, frame.snapshot()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(service.obdState(vehicleId).liveData()).isEmpty();
        assertThat(service.isVehicleDataConnected(vehicleId)).isFalse();
    }
}
