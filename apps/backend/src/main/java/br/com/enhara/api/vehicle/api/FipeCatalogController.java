package br.com.enhara.api.vehicle.api;

import br.com.enhara.api.vehicle.api.VehicleProfileModels.FipeOptionResponse;
import br.com.enhara.api.vehicle.application.provider.FipeCatalogProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/vehicle-data/fipe")
public class FipeCatalogController {
    private final FipeCatalogProvider provider;

    public FipeCatalogController(FipeCatalogProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/brands")
    public List<FipeOptionResponse> brands(@RequestParam(defaultValue = "CAR")
                                           FipeCatalogProvider.VehicleType vehicleType) {
        return provider.brands(vehicleType).stream().map(FipeOptionResponse::from).toList();
    }

    @GetMapping("/brands/{brandCode}/models")
    public List<FipeOptionResponse> models(@PathVariable String brandCode,
                                           @RequestParam(defaultValue = "CAR")
                                           FipeCatalogProvider.VehicleType vehicleType) {
        return provider.models(vehicleType, brandCode).stream().map(FipeOptionResponse::from).toList();
    }

    @GetMapping("/brands/{brandCode}/models/{modelCode}/years")
    public List<FipeOptionResponse> years(@PathVariable String brandCode, @PathVariable String modelCode,
                                          @RequestParam(defaultValue = "CAR")
                                          FipeCatalogProvider.VehicleType vehicleType) {
        return provider.years(vehicleType, brandCode, modelCode).stream().map(FipeOptionResponse::from).toList();
    }
}
