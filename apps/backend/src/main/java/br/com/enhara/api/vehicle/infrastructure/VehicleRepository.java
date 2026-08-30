package br.com.enhara.api.vehicle.infrastructure;

import br.com.enhara.api.vehicle.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    boolean existsByVinIgnoreCase(String vin);
    boolean existsByLicensePlateIgnoreCase(String licensePlate);
}
