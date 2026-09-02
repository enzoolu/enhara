package br.com.enhara.api.vehicle.infrastructure;

import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleProfileFieldRepository extends JpaRepository<VehicleProfileField, UUID> {
    List<VehicleProfileField> findByVehicleIdOrderByKeyAsc(UUID vehicleId);
    Optional<VehicleProfileField> findByVehicleIdAndKey(UUID vehicleId, VehicleProfileField.Key key);
}
