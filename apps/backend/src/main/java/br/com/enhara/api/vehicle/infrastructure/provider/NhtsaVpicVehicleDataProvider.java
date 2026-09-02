package br.com.enhara.api.vehicle.infrastructure.provider;

import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider;
import br.com.enhara.api.vehicle.application.provider.VehicleProviderException;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Component
public class NhtsaVpicVehicleDataProvider implements VehicleDataProvider {
    public static final String ID = "NHTSA_VPIC";
    private static final String SOURCE_URL = "https://vpic.nhtsa.dot.gov/api/";
    private final RestClient client;

    public NhtsaVpicVehicleDataProvider(
            @Value("${enhara.vehicle-data.nhtsa-base-url:https://vpic.nhtsa.dot.gov/api}") String baseUrl) {
        this.client = ProviderRestClientFactory.create(baseUrl);
    }

    @Override
    public String id() { return ID; }

    @Override
    public boolean supports(Lookup lookup) {
        return lookup instanceof VinLookup;
    }

    @Override
    public Duration cacheTtl() {
        return Duration.ofDays(30);
    }

    @Override
    public Data fetch(Lookup lookup) {
        if (!(lookup instanceof VinLookup vinLookup)) {
            throw new IllegalArgumentException("Consulta não suportada pelo NHTSA vPIC.");
        }
        try {
            JsonNode root = client.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/vehicles/DecodeVinValues/{vin}").queryParam("format", "json");
                        if (vinLookup.modelYear() != null) builder.queryParam("modelyear", vinLookup.modelYear());
                        return builder.build(vinLookup.vin());
                    })
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode result = root == null ? null : root.path("Results").path(0);
            if (result == null || result.isMissingNode() || result.isNull()) {
                throw new VehicleProviderException("NHTSA vPIC não retornou resultado para o VIN.");
            }

            Map<VehicleProfileField.Key, String> fields = new EnumMap<>(VehicleProfileField.Key.class);
            put(fields, VehicleProfileField.Key.MANUFACTURER, result.path("Make").asText());
            put(fields, VehicleProfileField.Key.MODEL, result.path("Model").asText());
            put(fields, VehicleProfileField.Key.MODEL_YEAR, result.path("ModelYear").asText());
            put(fields, VehicleProfileField.Key.VERSION, result.path("Trim").asText());
            put(fields, VehicleProfileField.Key.FUEL_TYPE, result.path("FuelTypePrimary").asText());
            put(fields, VehicleProfileField.Key.TRANSMISSION, result.path("TransmissionStyle").asText());
            String engine = clean(result.path("EngineModel").asText());
            if (engine == null) engine = displacement(result.path("DisplacementL").asText());
            put(fields, VehicleProfileField.Key.ENGINE, engine);
            if (fields.isEmpty()) {
                String errorText = clean(result.path("ErrorText").asText());
                throw new VehicleProviderException(errorText == null
                        ? "NHTSA vPIC não possui dados decodificados para este VIN."
                        : "NHTSA vPIC: " + errorText);
            }
            return new Data(fields, SOURCE_URL);
        } catch (VehicleProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VehicleProviderException("NHTSA vPIC indisponível no momento.", exception);
        }
    }

    private static String displacement(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            return new BigDecimal(clean).stripTrailingZeros().toPlainString() + " L";
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void put(Map<VehicleProfileField.Key, String> fields, VehicleProfileField.Key key, String value) {
        String clean = clean(value);
        if (clean != null) fields.put(key, clean);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) ? null : value.trim();
    }
}
