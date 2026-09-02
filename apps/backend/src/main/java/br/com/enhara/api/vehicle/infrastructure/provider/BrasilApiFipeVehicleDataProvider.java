package br.com.enhara.api.vehicle.infrastructure.provider;

import br.com.enhara.api.vehicle.application.provider.FipeCatalogProvider;
import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider;
import br.com.enhara.api.vehicle.application.provider.VehicleProviderException;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Component
public class BrasilApiFipeVehicleDataProvider implements VehicleDataProvider, FipeCatalogProvider {
    public static final String ID = "BRASILAPI_FIPE";
    private static final String SOURCE_URL = "https://brasilapi.com.br/docs#tag/FIPE";
    private final RestClient client;

    public BrasilApiFipeVehicleDataProvider(
            @Value("${enhara.vehicle-data.brasil-api-base-url:https://brasilapi.com.br/api}") String baseUrl) {
        this.client = ProviderRestClientFactory.create(baseUrl);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Lookup lookup) {
        return lookup instanceof FipeCodeLookup || lookup instanceof FipeSelectionLookup;
    }

    @Override
    public Duration cacheTtl() {
        return Duration.ofHours(24);
    }

    @Override
    public Data fetch(Lookup lookup) {
        if (lookup instanceof FipeCodeLookup codeLookup) return fetchByCode(codeLookup);
        if (lookup instanceof FipeSelectionLookup selection) return fetchBySelection(selection);
        throw new IllegalArgumentException("Consulta não suportada pela BrasilAPI/FIPE.");
    }

    @Override
    public List<Option> brands(VehicleType vehicleType) {
        return catalog("/fipe/marcas/v1/{vehicleType}", vehicleType.providerValue());
    }

    @Override
    public List<Option> models(VehicleType vehicleType, String brandCode) {
        requireNumericCode(brandCode, "marca");
        return catalog("/fipe/veiculos/v1/{vehicleType}/{brandCode}", vehicleType.providerValue(), brandCode);
    }

    @Override
    public List<Option> years(VehicleType vehicleType, String brandCode, String modelCode) {
        requireNumericCode(brandCode, "marca");
        requireNumericCode(modelCode, "modelo");
        return catalog("/fipe/anos/v1/{vehicleType}/{brandCode}/{modelCode}",
                vehicleType.providerValue(), brandCode, modelCode);
    }

    private Data fetchByCode(FipeCodeLookup lookup) {
        try {
            JsonNode root = client.get().uri("/fipe/preco/v1/{code}", lookup.fipeCode())
                    .retrieve().body(JsonNode.class);
            if (root == null || !root.isArray() || root.isEmpty()) {
                throw new VehicleProviderException("BrasilAPI/FIPE não retornou dados para o código informado.");
            }
            JsonNode result = selectModelYear(root, lookup.modelYear());
            if (result == null) {
                throw new VehicleProviderException(
                        "O código FIPE possui múltiplos anos; confirme um ano compatível no cadastro.");
            }
            return normalized(result);
        } catch (VehicleProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VehicleProviderException("BrasilAPI/FIPE indisponível no momento.", exception);
        }
    }

    private Data fetchBySelection(FipeSelectionLookup lookup) {
        requireNumericCode(lookup.brandCode(), "marca");
        requireNumericCode(lookup.modelCode(), "modelo");
        if (lookup.yearCode() == null || !lookup.yearCode().matches("^[0-9]{4,5}-[0-9]+$")) {
            throw new IllegalArgumentException("Código de ano/combustível FIPE inválido.");
        }
        try {
            JsonNode result = client.get().uri(
                            "/fipe/detalhes/v1/{vehicleType}/{brandCode}/{modelCode}/{yearCode}",
                            lookup.vehicleType().providerValue(), lookup.brandCode(), lookup.modelCode(),
                            lookup.yearCode())
                    .retrieve().body(JsonNode.class);
            if (result == null || !result.isObject() || result.isEmpty()) {
                throw new VehicleProviderException("BrasilAPI/FIPE não retornou detalhes para a seleção.");
            }
            return normalized(result);
        } catch (VehicleProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VehicleProviderException("BrasilAPI/FIPE indisponível no momento.", exception);
        }
    }

    private List<Option> catalog(String path, Object... variables) {
        try {
            JsonNode root = client.get().uri(path, variables).retrieve().body(JsonNode.class);
            if (root == null || !root.isArray()) {
                throw new VehicleProviderException("BrasilAPI/FIPE retornou um catálogo inválido.");
            }
            List<Option> result = new ArrayList<>();
            for (JsonNode item : root) {
                String code = clean(text(item, "valor", "code"));
                String label = clean(text(item, "nome", "modelo", "label"));
                if (code != null && label != null) result.add(new Option(code, label));
            }
            return List.copyOf(result);
        } catch (VehicleProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VehicleProviderException("Catálogo BrasilAPI/FIPE indisponível no momento.", exception);
        }
    }

    private static Data normalized(JsonNode result) {
        Map<VehicleProfileField.Key, String> fields = new EnumMap<>(VehicleProfileField.Key.class);
        put(fields, VehicleProfileField.Key.MANUFACTURER, text(result, "marca", "brand"));
        put(fields, VehicleProfileField.Key.MODEL, text(result, "modelo", "model"));
        put(fields, VehicleProfileField.Key.MODEL_YEAR, text(result, "ano_modelo", "anoModelo", "modelYear"));
        put(fields, VehicleProfileField.Key.FUEL_TYPE, text(result, "combustivel", "fuel"));
        put(fields, VehicleProfileField.Key.FIPE_CODE, text(result, "codigo_fipe", "codigoFipe", "fipeCode"));
        put(fields, VehicleProfileField.Key.FIPE_VALUE, text(result, "valor", "price"));
        put(fields, VehicleProfileField.Key.FIPE_REFERENCE_MONTH,
                text(result, "mes_referencia", "mesReferencia", "referenceMonth"));
        if (!fields.containsKey(VehicleProfileField.Key.FIPE_CODE)) {
            throw new VehicleProviderException("BrasilAPI/FIPE retornou detalhes sem código FIPE confiável.");
        }
        return new Data(fields, SOURCE_URL);
    }

    private static JsonNode selectModelYear(JsonNode root, Integer modelYear) {
        if (modelYear != null) {
            return StreamSupport.stream(root.spliterator(), false)
                    .filter(item -> item.path("ano_modelo").asInt(-1) == modelYear)
                    .findFirst().orElse(null);
        }
        return root.size() == 1 ? root.get(0) : null;
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) return value.asText();
        }
        return null;
    }

    private static void requireNumericCode(String code, String label) {
        if (code == null || !code.matches("^[0-9]+$")) {
            throw new IllegalArgumentException("Código FIPE de " + label + " inválido.");
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
