package br.com.enhara.api.diagnostics.infrastructure;

import br.com.enhara.api.diagnostics.domain.Diagnostic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosticRepository extends JpaRepository<Diagnostic, UUID> {
    List<Diagnostic> findByVehicleIdOrderByDetectedAtDesc(UUID vehicleId);
    List<Diagnostic> findByVehicleIdAndStatusOrderByDetectedAtDesc(UUID vehicleId, Diagnostic.Status status);
    Optional<Diagnostic> findFirstByVehicleIdAndCodeAndStatus(UUID vehicleId, String code, Diagnostic.Status status);
}
