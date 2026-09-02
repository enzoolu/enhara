package br.com.enhara.api.vehicle;

import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.vehicle.application.VehicleService;
import br.com.enhara.api.vehicle.infrastructure.VehicleProviderCacheRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleEnrichmentFlowIntegrationTest {
    private static final AtomicBoolean FIPE_OFFLINE = new AtomicBoolean();
    private static final HttpServer PROVIDERS = startProviders();

    @Autowired MockMvc mockMvc;
    @Autowired VehicleService vehicles;
    @Autowired VehicleProviderCacheRepository cache;

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        String baseUrl = "http://127.0.0.1:" + PROVIDERS.getAddress().getPort();
        registry.add("enhara.vehicle-data.brasil-api-base-url", () -> baseUrl);
        registry.add("enhara.vehicle-data.nhtsa-base-url", () -> baseUrl);
    }

    @AfterAll
    static void stopProviders() {
        PROVIDERS.stop(0);
    }

    @Test
    void guidedFipePersistsNormalizedDataPreservesManualCorrectionAndFallsBackToStaleCache() throws Exception {
        var vehicle = vehicles.create(new CreateVehicleRequest("Carro enriquecido", "1HGCM82633A004351",
                "Cadastro", "Modelo cadastro", 2003, "ENR1Q23", 0));

        mockMvc.perform(get("/api/vehicle-data/fipe/brands").queryParam("vehicleType", "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("21"));
        mockMvc.perform(get("/api/vehicle-data/fipe/brands/21/models").queryParam("vehicleType", "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("4828"));
        mockMvc.perform(get("/api/vehicle-data/fipe/brands/21/models/4828/years")
                        .queryParam("vehicleType", "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("2003-1"));

        mockMvc.perform(put("/api/vehicles/{vehicleId}/profile/manual", vehicle.getId())
                        .contentType("application/json")
                        .content("{\"fields\":{\"MODEL\":\"Modelo corrigido pelo usuário\",\"FIPE_CODE\":\"999999-9\"}}"))
                .andExpect(status().isOk());

        String request = """
                {"fipeCode":null,"fipeSelection":{"vehicleType":"CAR","brandCode":"21",
                 "modelCode":"4828","yearCode":"2003-1"},"forceRefresh":false}
                """;
        mockMvc.perform(post("/api/vehicles/{vehicleId}/profile/enrich", vehicle.getId())
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.key == 'MODEL')].value",
                        hasItem("Modelo corrigido pelo usuário")))
                .andExpect(jsonPath("$.fields[?(@.key == 'MODEL')].provenance.source",
                        hasItem("USER_PROVIDED")))
                .andExpect(jsonPath("$.fields[?(@.key == 'FIPE_CODE')].value", hasItem("001004-9")))
                .andExpect(jsonPath("$.providers[?(@.provider == 'BRASILAPI_FIPE')].state", hasItem("LIVE")))
                .andExpect(jsonPath("$.providers[?(@.provider == 'NHTSA_VPIC')].state", hasItem("CONFLICT")));

        var entry = cache.findByProviderAndLookupKey("BRASILAPI_FIPE",
                        "FIPE_SELECTION:CAR:21:4828:2003-1").orElseThrow();
        entry.update(entry.getPayloadJson(), entry.getFetchedAt(), Instant.now().minusSeconds(1));
        cache.saveAndFlush(entry);
        FIPE_OFFLINE.set(true);

        try {
            mockMvc.perform(post("/api/vehicles/{vehicleId}/profile/enrich", vehicle.getId())
                            .contentType("application/json")
                            .content(request.replace("false", "true")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fields[?(@.key == 'FIPE_CODE')].value", hasItem("001004-9")))
                    .andExpect(jsonPath("$.fields[?(@.key == 'FIPE_CODE')].provenance.stale", hasItem(true)))
                    .andExpect(jsonPath("$.providers[?(@.provider == 'BRASILAPI_FIPE')].state",
                            hasItem("CACHE_STALE")));
        } finally {
            FIPE_OFFLINE.set(false);
        }
    }

    private static HttpServer startProviders() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/fipe/marcas/v1/carros", exchange -> json(exchange,
                    "[{\"nome\":\"Fiat\",\"valor\":\"21\"}]"));
            server.createContext("/fipe/veiculos/v1/carros/21", exchange -> json(exchange,
                    "[{\"modelo\":\"Palio EX 1.0 mpi 2p\",\"valor\":\"4828\"}]"));
            server.createContext("/fipe/anos/v1/carros/21/4828", exchange -> json(exchange,
                    "[{\"nome\":\"2003 Gasolina\",\"valor\":\"2003-1\"}]"));
            server.createContext("/fipe/detalhes/v1/carros/21/4828/2003-1", exchange -> {
                if (FIPE_OFFLINE.get()) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                } else {
                    json(exchange, """
                            {"valor":"R$ 20.000,00","marca":"Fiat","modelo":"Palio EX 1.0 mpi 2p",
                             "anoModelo":2003,"combustivel":"Gasolina","codigoFipe":"001004-9",
                             "mesReferencia":"setembro de 2026"}
                            """);
                }
            });
            server.createContext("/vehicles/DecodeVinValues/1HGCM82633A004351", exchange -> json(exchange, """
                    {"Count":1,"Results":[{"Make":"HONDA","Model":"Accord","ModelYear":"2003",
                      "Trim":"EX","FuelTypePrimary":"Gasoline","TransmissionStyle":"Automatic",
                      "EngineModel":"K24A4","ErrorCode":"0"}]}
                    """));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
