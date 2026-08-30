package br.com.enhara.api.alerts.infrastructure;

import br.com.enhara.api.alerts.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);
    List<Alert> findByVehicleIdAndStatusOrderByCreatedAtDesc(UUID vehicleId, Alert.Status status);
    Optional<Alert> findFirstByVehicleIdAndTypeAndStatus(UUID vehicleId, Alert.Type type, Alert.Status status);
}
