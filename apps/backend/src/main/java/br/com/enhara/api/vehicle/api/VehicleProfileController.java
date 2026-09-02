package br.com.enhara.api.vehicle.api;

import br.com.enhara.api.vehicle.api.VehicleProfileModels.ConfirmProfileRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.EcuVinRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.EnrichProfileRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.ManualProfileRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.VehicleProfileResponse;
import br.com.enhara.api.vehicle.application.VehicleProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/profile")
public class VehicleProfileController {
    private final VehicleProfileService profiles;

    public VehicleProfileController(VehicleProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public VehicleProfileResponse get(@PathVariable UUID vehicleId) {
        return profiles.get(vehicleId);
    }

    @PutMapping("/manual")
    public VehicleProfileResponse updateManual(@PathVariable UUID vehicleId,
                                               @Valid @RequestBody ManualProfileRequest request) {
        return profiles.updateManual(vehicleId, request);
    }

    @PostMapping("/confirm")
    public VehicleProfileResponse confirm(@PathVariable UUID vehicleId,
                                          @Valid @RequestBody ConfirmProfileRequest request) {
        return profiles.confirm(vehicleId, request);
    }

    @PostMapping("/enrich")
    public VehicleProfileResponse enrich(@PathVariable UUID vehicleId,
                                         @Valid @RequestBody EnrichProfileRequest request) {
        return profiles.enrich(vehicleId, request);
    }

    /**
     * Integration boundary for a real OBD adapter. The simulator deliberately has no call path to this endpoint.
     */
    @PostMapping("/ecu-vin")
    public VehicleProfileResponse recordRealEcuVin(@PathVariable UUID vehicleId,
                                                   @Valid @RequestBody EcuVinRequest request) {
        return profiles.recordRealEcuVin(vehicleId, request);
    }
}
