package br.com.enhara.api.vehicle.application.provider;

import br.com.enhara.api.vehicle.domain.VehicleProfileField;

import java.time.Duration;
import java.util.Map;

public interface VehicleDataProvider {
    sealed interface Lookup permits VinLookup, FipeCodeLookup, FipeSelectionLookup {
        String cacheKey();
    }

    record VinLookup(String vin, Integer modelYear) implements Lookup {
        @Override
        public String cacheKey() {
            return "VIN:" + vin.toUpperCase() + ":" + (modelYear == null ? "" : modelYear);
        }
    }

    record FipeCodeLookup(String fipeCode, Integer modelYear) implements Lookup {
        @Override
        public String cacheKey() {
            return "FIPE_CODE:" + fipeCode + ":" + (modelYear == null ? "" : modelYear);
        }
    }

    record FipeSelectionLookup(FipeCatalogProvider.VehicleType vehicleType, String brandCode,
                               String modelCode, String yearCode) implements Lookup {
        @Override
        public String cacheKey() {
            return "FIPE_SELECTION:" + vehicleType + ":" + brandCode + ":" + modelCode + ":" + yearCode;
        }
    }

    record Data(Map<VehicleProfileField.Key, String> fields, String sourceUrl) {
        public Data {
            fields = Map.copyOf(fields);
        }
    }

    String id();
    boolean supports(Lookup lookup);
    Duration cacheTtl();
    Data fetch(Lookup lookup);
}
