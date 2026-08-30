package br.com.enhara.api.trips.infrastructure;

import br.com.enhara.api.trips.domain.Trip;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findFirstByVehicleIdAndEndedAtIsNullOrderByStartedAtDesc(UUID vehicleId);
    List<Trip> findByVehicleIdOrderByStartedAtDesc(UUID vehicleId, Pageable pageable);
}
