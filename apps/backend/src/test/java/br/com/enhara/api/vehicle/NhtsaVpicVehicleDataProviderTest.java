package br.com.enhara.api.vehicle;

import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider.VinLookup;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import br.com.enhara.api.vehicle.infrastructure.provider.NhtsaVpicVehicleDataProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NhtsaVpicVehicleDataProviderTest {
    private HttpServer server;
    private NhtsaVpicVehicleDataProvider provider;

    @BeforeEach
    void startProviderStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vehicles/DecodeVinValues/1HGCM82633A004352", exchange -> json(exchange, """
                {"Count":1,"Results":[{"Make":"HONDA","Model":"Accord","ModelYear":"2003",
                  "Trim":"EX","FuelTypePrimary":"Gasoline","TransmissionStyle":"Automatic",
                  "EngineModel":"K24A4","DisplacementL":"2.4","ErrorCode":"0"}]}
                """));
        server.start();
        provider = new NhtsaVpicVehicleDataProvider("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopProviderStub() {
        server.stop(0);
    }

    @Test
    void normalizesOnlyAttributesReturnedByVpic() {
        var data = provider.fetch(new VinLookup("1HGCM82633A004352", 2003));

        assertThat(data.fields())
                .containsEntry(VehicleProfileField.Key.MANUFACTURER, "HONDA")
                .containsEntry(VehicleProfileField.Key.MODEL, "Accord")
                .containsEntry(VehicleProfileField.Key.VERSION, "EX")
                .containsEntry(VehicleProfileField.Key.ENGINE, "K24A4")
                .containsEntry(VehicleProfileField.Key.TRANSMISSION, "Automatic")
                .doesNotContainKeys(VehicleProfileField.Key.FIPE_CODE, VehicleProfileField.Key.FIPE_VALUE);
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
