package br.com.enhara.api.notes.api;

import br.com.enhara.api.notes.application.VehicleNoteService;
import br.com.enhara.api.shared.api.ApiModels.VehicleNoteRequest;
import br.com.enhara.api.shared.api.ApiModels.VehicleNoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/notes")
public class VehicleNoteController {
    private final VehicleNoteService notes;

    public VehicleNoteController(VehicleNoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<VehicleNoteResponse> list(@PathVariable UUID vehicleId,
                                          @RequestParam(defaultValue = "false") boolean includeCompleted) {
        Instant now = Instant.now();
        return notes.list(vehicleId, includeCompleted).stream().map(note -> VehicleNoteResponse.from(note, now)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleNoteResponse create(@PathVariable UUID vehicleId, @Valid @RequestBody VehicleNoteRequest request) {
        return VehicleNoteResponse.from(notes.create(vehicleId, request), Instant.now());
    }

    @PutMapping("/{noteId}")
    public VehicleNoteResponse update(@PathVariable UUID vehicleId, @PathVariable UUID noteId,
                                      @Valid @RequestBody VehicleNoteRequest request) {
        return VehicleNoteResponse.from(notes.update(vehicleId, noteId, request), Instant.now());
    }

    @PostMapping("/{noteId}/complete")
    public VehicleNoteResponse complete(@PathVariable UUID vehicleId, @PathVariable UUID noteId) {
        return VehicleNoteResponse.from(notes.complete(vehicleId, noteId), Instant.now());
    }

    @PostMapping("/{noteId}/reopen")
    public VehicleNoteResponse reopen(@PathVariable UUID vehicleId, @PathVariable UUID noteId) {
        return VehicleNoteResponse.from(notes.reopen(vehicleId, noteId), Instant.now());
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID vehicleId, @PathVariable UUID noteId) {
        notes.delete(vehicleId, noteId);
    }
}
