package br.com.enhara.api.telemetry.infrastructure;

import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelemetryRepository extends JpaRepository<TelemetrySample, Long> {
    Optional<TelemetrySample> findTopByVehicleIdOrderByRecordedAtDesc(UUID vehicleId);
    List<TelemetrySample> findByVehicleIdOrderByRecordedAtDesc(UUID vehicleId, Pageable pageable);
    List<TelemetrySample> findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID vehicleId,
                                                                                  Instant startedAt,
                                                                                  Instant endedAt);
}
