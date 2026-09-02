package br.com.enhara.api.simulator.application;

import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.simulator.domain.SimulationScenario;
import br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId;
import br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.DtcStatus;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulationServiceIntegrationTest {
    @Autowired SimulationService simulation;
    @Autowired VehicleService vehicles;
    @Autowired TelemetryService telemetry;

    @Test
    void ecuDtcRemainsSeparateFromEnharaFindingAndAlert() {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro ECU", "8AGZZZ377VT004259",
                "Demo", "ECU", 2026, "TST1A09", 0));
        simulation.setScenario(vehicle.getId(), SimulationScenario.MISFIRE);

        for (int index = 0; index < 6; index++) simulation.tick(vehicle.getId());

        var obd = simulation.obdState(vehicle.getId());
        assertThat(obd.dtcs()).singleElement().satisfies(dtc -> {
            assertThat(dtc.code()).isEqualTo("P0300");
            assertThat(dtc.statuses()).contains(DtcStatus.PENDING, DtcStatus.CONFIRMED, DtcStatus.PERMANENT);
            assertThat(dtc.freezeFrame()).isNotNull();
        });
        assertThat(obd.milOn()).isTrue();
        assertThat(telemetry.diagnostics(vehicle.getId(), false))
                .noneMatch(finding -> finding.getCode().equals("P0300"));
        assertThat(telemetry.alerts(vehicle.getId(), false)).isEmpty();
        assertThat(telemetry.latest(vehicle.getId())).get()
                .extracting(TelemetrySample::getSource).isEqualTo(TelemetrySample.Source.SIMULATED_OBD);
    }

    @Test
    void profileCanChangeWithoutInventingUnsupportedPidValues() {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro limitado", "8AGZZZ377VT004258",
                "Demo", "Limited", 2026, "TST1A08", 0));
        simulation.setProfile(vehicle.getId(), ProfileId.COMPACT_GASOLINE_LIMITED);
        simulation.tick(vehicle.getId());

        var obd = simulation.obdState(vehicle.getId());
        assertThat(obd.profile()).isEqualTo(ProfileId.COMPACT_GASOLINE_LIMITED);
        assertThat(obd.liveData()).noneMatch(value -> value.key().equals("MAF_AIR_FLOW_RATE"));
        assertThat(obd.capabilities()).anyMatch(capability -> capability.key().equals("MAF_AIR_FLOW_RATE")
                && capability.status().name().equals("UNSUPPORTED"));
    }
}
