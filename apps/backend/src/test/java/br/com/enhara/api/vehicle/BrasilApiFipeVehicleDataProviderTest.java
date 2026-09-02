package br.com.enhara.api.vehicle;

import br.com.enhara.api.vehicle.application.provider.FipeCatalogProvider.VehicleType;
import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider.FipeCodeLookup;
import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider.FipeSelectionLookup;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import br.com.enhara.api.vehicle.infrastructure.provider.BrasilApiFipeVehicleDataProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BrasilApiFipeVehicleDataProviderTest {
    private HttpServer server;
    private BrasilApiFipeVehicleDataProvider provider;

    @BeforeEach
    void startProviderStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fipe/marcas/v1/carros", exchange -> json(exchange,
                "[{\"nome\":\"Fiat\",\"valor\":\"21\"}]"));
        server.createContext("/fipe/veiculos/v1/carros/21", exchange -> json(exchange,
                "[{\"modelo\":\"Palio EX 1.0 mpi 2p\",\"valor\":\"4828\"}]"));
        server.createContext("/fipe/anos/v1/carros/21/4828", exchange -> json(exchange,
                "[{\"nome\":\"1998 Gasolina\",\"valor\":\"1998-1\"}]"));
        server.createContext("/fipe/detalhes/v1/carros/21/4828/1998-1", exchange -> json(exchange, """
                {"valor":"R$ 15.321,00","marca":"Fiat","modelo":"Palio EX 1.0 mpi 2p",
                 "anoModelo":1998,"combustivel":"Gasolina","codigoFipe":"001004-9",
                 "mesReferencia":"setembro de 2026"}
                """));
        server.createContext("/fipe/preco/v1/001004-9", exchange -> json(exchange, """
                [{"valor":"R$ 15.321,00","marca":"Fiat","modelo":"Palio EX 1.0 mpi 2p",
                  "ano_modelo":1998,"combustivel":"Gasolina","codigo_fipe":"001004-9",
                  "mes_referencia":"setembro de 2026"}]
                """));
        server.start();
        provider = new BrasilApiFipeVehicleDataProvider("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopProviderStub() {
        server.stop(0);
    }

    @Test
    void exposesGuidedCatalogAndNormalizesSelectedDetails() {
        assertThat(provider.brands(VehicleType.CAR)).singleElement()
                .satisfies(option -> assertThat(option.label()).isEqualTo("Fiat"));
        assertThat(provider.models(VehicleType.CAR, "21")).singleElement()
                .satisfies(option -> assertThat(option.code()).isEqualTo("4828"));
        assertThat(provider.years(VehicleType.CAR, "21", "4828")).singleElement()
                .satisfies(option -> assertThat(option.code()).isEqualTo("1998-1"));

        var data = provider.fetch(new FipeSelectionLookup(VehicleType.CAR, "21", "4828", "1998-1"));

        assertThat(data.fields())
                .containsEntry(VehicleProfileField.Key.MANUFACTURER, "Fiat")
                .containsEntry(VehicleProfileField.Key.MODEL, "Palio EX 1.0 mpi 2p")
                .containsEntry(VehicleProfileField.Key.MODEL_YEAR, "1998")
                .containsEntry(VehicleProfileField.Key.FUEL_TYPE, "Gasolina")
                .containsEntry(VehicleProfileField.Key.FIPE_CODE, "001004-9")
                .containsEntry(VehicleProfileField.Key.FIPE_VALUE, "R$ 15.321,00")
                .doesNotContainKeys(VehicleProfileField.Key.ENGINE, VehicleProfileField.Key.TRANSMISSION);
    }

    @Test
    void normalizesLegacyPricePayloadWithoutInventingSpecifications() {
        var data = provider.fetch(new FipeCodeLookup("001004-9", 1998));

        assertThat(data.fields()).containsEntry(VehicleProfileField.Key.FIPE_REFERENCE_MONTH, "setembro de 2026");
        assertThat(data.fields()).doesNotContainKeys(VehicleProfileField.Key.ENGINE,
                VehicleProfileField.Key.TRANSMISSION, VehicleProfileField.Key.VERSION);
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
