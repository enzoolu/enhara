package br.com.enhara.api.vehicle;

import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VehicleProfileApiIntegrationTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired MockMvc mockMvc;
    @Autowired VehicleService vehicles;

    @Test
    void corsAllowsProfileAndPhotoMutationsFromWebDashboard() throws Exception {
        mockMvc.perform(options("/api/vehicles/00000000-0000-0000-0000-000000000000/profile/manual")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("PUT"));
        mockMvc.perform(options("/api/vehicles/00000000-0000-0000-0000-000000000000/photos/photo-id")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("DELETE"));
    }

    @Test
    void manualRegistrationAcceptsMissingVin() throws Exception {
        String payload = mockMvc.perform(post("/api/vehicles")
                        .contentType("application/json")
                        .content("""
                                {"name":"Carro sem VIN","vin":null,"manufacturer":"Fiat","model":"Argo",
                                 "modelYear":2022,"licensePlate":"NOV1N22","odometerKm":12000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vin").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String vehicleId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).path("id").asText();

        mockMvc.perform(get("/api/vehicles/{vehicleId}/profile", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key == 'VIN')]").isEmpty())
                .andExpect(jsonPath("$.fields[?(@.key == 'MODEL')].value", hasItem("Argo")));
    }

    @Test
    void profileTracksRegistrationManualAndRealObdProvenance() throws Exception {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro perfil", "1HGCM82633A004352",
                "Honda", "Accord", 2003, "PRF1A01", 0));

        mockMvc.perform(get("/api/vehicles/{vehicleId}/profile", vehicle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key == 'MODEL')].value", hasItem("Accord")))
                .andExpect(jsonPath("$.fields[?(@.key == 'MODEL')].provenance.source",
                        hasItem("VEHICLE_REGISTRATION")));

        mockMvc.perform(put("/api/vehicles/{vehicleId}/profile/manual", vehicle.getId())
                        .contentType("application/json")
                        .content("""
                                {"fields":{"MANUFACTURER":"Honda do Brasil","VERSION":"EX informado"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key == 'VERSION')].value", hasItem("EX informado")))
                .andExpect(jsonPath("$.fields[?(@.key == 'VERSION')].provenance.source",
                        hasItem("USER_PROVIDED")))
                .andExpect(jsonPath("$.fields[?(@.key == 'VERSION')].provenance.confirmedAt").exists());

        mockMvc.perform(post("/api/vehicles/{vehicleId}/profile/ecu-vin", vehicle.getId())
                        .contentType("application/json")
                        .content("""
                                {"vin":"3FA6P0H73HR123456","source":"SIMULATED_OBD"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/vehicles/{vehicleId}/profile/ecu-vin", vehicle.getId())
                        .contentType("application/json")
                        .content("""
                                {"vin":"3FA6P0H73HR123456","source":"REAL_OBD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key == 'VIN')].provenance.source", hasItem("ECU_OBD")));
    }

    @Test
    void userPhotoIsPersistedServedAndDeleted() throws Exception {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro foto", "8AGZZZ377VT004289",
                "Demo", "Foto", 2026, "PRF1A02", 0));
        var file = new MockMultipartFile("file", "meu-carro.png", "image/png", PNG);

        String payload = mockMvc.perform(multipart("/api/vehicles/{vehicleId}/photos", vehicle.getId())
                        .file(file).param("caption", "Frente do veículo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("image/png"))
                .andExpect(jsonPath("$.source").value("USER_PROVIDED"))
                .andExpect(jsonPath("$.caption").value("Frente do veículo"))
                .andReturn().getResponse().getContentAsString();
        String photoId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).path("id").asText();

        mockMvc.perform(get("/api/vehicles/{vehicleId}/photos/{photoId}/content", vehicle.getId(), photoId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        mockMvc.perform(delete("/api/vehicles/{vehicleId}/photos/{photoId}", vehicle.getId(), photoId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/vehicles/{vehicleId}/photos/{photoId}/content", vehicle.getId(), photoId))
                .andExpect(status().isNotFound());
    }
}
