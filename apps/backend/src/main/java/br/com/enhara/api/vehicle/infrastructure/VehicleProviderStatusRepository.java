package br.com.enhara.api.vehicle.infrastructure;

import br.com.enhara.api.vehicle.domain.VehicleProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleProviderStatusRepository extends JpaRepository<VehicleProviderStatus, UUID> {
    List<VehicleProviderStatus> findByVehicleIdOrderByProviderAsc(UUID vehicleId);
    Optional<VehicleProviderStatus> findByVehicleIdAndProvider(UUID vehicleId, String provider);
}
