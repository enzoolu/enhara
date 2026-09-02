package br.com.enhara.api.notes.infrastructure;

import br.com.enhara.api.notes.domain.VehicleNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleNoteRepository extends JpaRepository<VehicleNote, UUID> {
    List<VehicleNote> findByVehicleIdOrderByUpdatedAtDesc(UUID vehicleId);
    List<VehicleNote> findByVehicleIdAndStatusOrderByUpdatedAtDesc(UUID vehicleId, VehicleNote.Status status);
}
