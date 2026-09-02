package br.com.enhara.api.vehicle.infrastructure;

import br.com.enhara.api.vehicle.domain.VehiclePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehiclePhotoRepository extends JpaRepository<VehiclePhoto, UUID> {
    List<VehiclePhoto> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);
}
