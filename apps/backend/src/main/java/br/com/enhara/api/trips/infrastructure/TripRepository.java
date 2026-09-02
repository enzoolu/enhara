package br.com.enhara.api.trips.infrastructure;

import br.com.enhara.api.trips.domain.Trip;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findFirstByVehicleIdAndEndedAtIsNullOrderByStartedAtDesc(UUID vehicleId);
    List<Trip> findByVehicleIdOrderByStartedAtDesc(UUID vehicleId, Pageable pageable);

    @Query("select sum(trip.distanceKm) from Trip trip where trip.vehicleId = :vehicleId and trip.endedAt is not null")
    Double sumCompletedDistanceKm(@Param("vehicleId") UUID vehicleId);

    long countByVehicleIdAndEndedAtIsNotNull(UUID vehicleId);
}
