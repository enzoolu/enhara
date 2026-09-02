package br.com.enhara.api.vehicle.application.provider;

import java.util.List;

/**
 * Port for the guided FIPE catalog. Provider-specific paths and payloads stay in infrastructure.
 */
public interface FipeCatalogProvider {
    enum VehicleType {
        CAR("carros"), MOTORCYCLE("motos"), TRUCK("caminhoes");

        private final String providerValue;

        VehicleType(String providerValue) {
            this.providerValue = providerValue;
        }

        public String providerValue() {
            return providerValue;
        }
    }

    record Option(String code, String label) {
    }

    List<Option> brands(VehicleType vehicleType);

    List<Option> models(VehicleType vehicleType, String brandCode);

    List<Option> years(VehicleType vehicleType, String brandCode, String modelCode);
}
