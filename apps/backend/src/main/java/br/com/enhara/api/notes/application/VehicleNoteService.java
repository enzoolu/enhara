package br.com.enhara.api.notes.application;

import br.com.enhara.api.notes.domain.VehicleNote;
import br.com.enhara.api.notes.infrastructure.VehicleNoteRepository;
import br.com.enhara.api.shared.api.ApiModels.VehicleNoteRequest;
import br.com.enhara.api.shared.error.ResourceNotFoundException;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleNoteService {
    private final VehicleService vehicles;
    private final VehicleNoteRepository notes;

    public VehicleNoteService(VehicleService vehicles, VehicleNoteRepository notes) {
        this.vehicles = vehicles;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<VehicleNote> list(UUID vehicleId, boolean includeCompleted) {
        vehicles.get(vehicleId);
        return includeCompleted
                ? notes.findByVehicleIdOrderByUpdatedAtDesc(vehicleId)
                : notes.findByVehicleIdAndStatusOrderByUpdatedAtDesc(vehicleId, VehicleNote.Status.OPEN);
    }

    @Transactional
    public VehicleNote create(UUID vehicleId, VehicleNoteRequest request) {
        vehicles.get(vehicleId);
        Instant now = Instant.now();
        return notes.save(new VehicleNote(vehicleId, request.title(), request.description(), request.category(),
                request.dueAt(), now));
    }

    @Transactional
    public VehicleNote update(UUID vehicleId, UUID noteId, VehicleNoteRequest request) {
        VehicleNote note = getForVehicle(vehicleId, noteId);
        note.update(request.title(), request.description(), request.category(), request.dueAt(), Instant.now());
        return note;
    }

    @Transactional
    public VehicleNote complete(UUID vehicleId, UUID noteId) {
        VehicleNote note = getForVehicle(vehicleId, noteId);
        note.complete(Instant.now());
        return note;
    }

    @Transactional
    public VehicleNote reopen(UUID vehicleId, UUID noteId) {
        VehicleNote note = getForVehicle(vehicleId, noteId);
        note.reopen(Instant.now());
        return note;
    }

    @Transactional
    public void delete(UUID vehicleId, UUID noteId) {
        VehicleNote note = getForVehicle(vehicleId, noteId);
        notes.delete(note);
    }

    private VehicleNote getForVehicle(UUID vehicleId, UUID noteId) {
        vehicles.get(vehicleId);
        return notes.findById(noteId)
                .filter(note -> note.getVehicleId().equals(vehicleId))
                .orElseThrow(() -> new ResourceNotFoundException("Nota não encontrada: " + noteId));
    }
}
