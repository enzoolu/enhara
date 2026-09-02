package br.com.enhara.api.simulator.api;

import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.simulator.domain.SimulationScenario;
import br.com.enhara.api.simulator.domain.StatefulVehicleSimulator;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.vehicle.application.VehicleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId.COMPACT_GASOLINE;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SimulationControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired VehicleService vehicles;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void profileAndObdEndpointsExposeCapabilityAwareSnapshot() throws Exception {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro HTTP limitado", "8AGZZZ377VT004257",
                "Demo", "HTTP Limited", 2026, "TST1A07", 0));

        mockMvc.perform(post("/api/vehicles/{vehicleId}/simulation/profile/{profile}",
                        vehicle.getId(), "COMPACT_GASOLINE_LIMITED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("COMPACT_GASOLINE_LIMITED"))
                .andExpect(jsonPath("$.generatedSamples").value(0));

        mockMvc.perform(get("/api/vehicles/{vehicleId}/simulation/obd", vehicle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("COMPACT_GASOLINE_LIMITED"))
                .andExpect(jsonPath("$.capabilities[?(@.key == 'MAF_AIR_FLOW_RATE')].status",
                        contains("UNSUPPORTED")))
                .andExpect(jsonPath("$.vehicleInformation").doesNotExist())
                .andExpect(jsonPath("$.dtcs").isEmpty());
    }

    @Test
    void scenarioAndTickEndpointsDriveDtcLifecycleInsideTheEcu() throws Exception {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro HTTP ECU", "8AGZZZ377VT004256",
                "Demo", "HTTP ECU", 2026, "TST1A06", 0));

        mockMvc.perform(post("/api/vehicles/{vehicleId}/simulation/scenario/{scenario}",
                        vehicle.getId(), "MISFIRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("MISFIRE"));
        for (int index = 0; index < 6; index++) {
            mockMvc.perform(post("/api/vehicles/{vehicleId}/simulation/tick", vehicle.getId()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/vehicles/{vehicleId}/simulation/obd", vehicle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("MISFIRE"))
                .andExpect(jsonPath("$.dtcs[0].code").value("P0300"))
                .andExpect(jsonPath("$.dtcs[0].statuses",
                        containsInAnyOrder("PENDING", "CONFIRMED", "PERMANENT")))
                .andExpect(jsonPath("$.dtcs[0].freezeFrame.values").isNotEmpty())
                .andExpect(jsonPath("$.milOn").value(true));
    }

    @Test
    void mobileBatchCarriesTheSameObdStateThroughTelemetryDiagnosticsAndDashboard() throws Exception {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro mobile integrado", "8AGZZZ377VT004260",
                "Demo", "Mobile ECU", 2026, "TST1A10", 0));
        var ecu = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        ecu.setScenario(SimulationScenario.OVERHEAT);
        Instant start = Instant.now().minusSeconds(35);
        var samples = new ArrayList<TelemetryRequest>();
        StatefulVehicleSimulator.Frame lastFrame = null;
        for (int index = 0; index < 36; index++) {
            lastFrame = ecu.tick(start.plusSeconds(index));
            samples.add(new TelemetryRequest(start.plusSeconds(index), lastFrame.speedKph(), lastFrame.rpm(),
                    lastFrame.engineTempC(), lastFrame.engineLoadPercent(), lastFrame.throttlePositionPercent(),
                    lastFrame.controlModuleVoltage(), lastFrame.fuelLevelPercent(), null, null,
                    TelemetrySample.Source.SIMULATED_OBD));
        }

        mockMvc.perform(post("/api/telemetry/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "vehicleId", vehicle.getId(),
                                "samples", samples,
                                "obdSnapshot", lastFrame.snapshot()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedSamples").value(36));

        mockMvc.perform(get("/api/vehicles/{vehicleId}/simulation/obd", vehicle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("OVERHEAT"))
                .andExpect(jsonPath("$.capabilities[?(@.key == 'ENGINE_SPEED')].availability",
                        contains("SUPPORTED")))
                .andExpect(jsonPath("$.dtcs[?(@.code == 'P0217')]").isNotEmpty());

        mockMvc.perform(get("/api/vehicles/{vehicleId}/dashboard", vehicle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleDataConnected").value(true))
                .andExpect(jsonPath("$.latestTelemetry.source").value("SIMULATED_OBD"))
                .andExpect(jsonPath("$.activeDiagnostics[?(@.code == 'ENGINE_TEMPERATURE_HIGH')]").isNotEmpty())
                .andExpect(jsonPath("$.openAlerts[?(@.type == 'ENGINE_OVERHEAT')]").isNotEmpty());
    }
}
