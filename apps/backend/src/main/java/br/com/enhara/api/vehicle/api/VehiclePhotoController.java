package br.com.enhara.api.vehicle.api;

import br.com.enhara.api.vehicle.api.VehicleProfileModels.VehiclePhotoResponse;
import br.com.enhara.api.vehicle.application.VehiclePhotoService;
import br.com.enhara.api.vehicle.domain.VehiclePhoto;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/photos")
public class VehiclePhotoController {
    private final VehiclePhotoService photos;

    public VehiclePhotoController(VehiclePhotoService photos) {
        this.photos = photos;
    }

    @GetMapping
    public List<VehiclePhotoResponse> list(@PathVariable UUID vehicleId) {
        return photos.list(vehicleId).stream().map(VehiclePhotoController::response).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VehiclePhotoResponse create(@PathVariable UUID vehicleId,
                                       @RequestPart("file") MultipartFile file,
                                       @RequestParam(required = false) String caption) {
        return response(photos.create(vehicleId, file, caption));
    }

    @GetMapping("/{photoId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID vehicleId, @PathVariable UUID photoId) {
        VehiclePhotoService.PhotoContent content = photos.content(vehicleId, photoId);
        VehiclePhoto photo = content.metadata();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getMediaType()))
                .contentLength(content.bytes().length)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFilename(photo.getOriginalFilename()))
                .body(content.bytes());
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID vehicleId, @PathVariable UUID photoId) {
        photos.delete(vehicleId, photoId);
    }

    private static VehiclePhotoResponse response(VehiclePhoto photo) {
        return new VehiclePhotoResponse(photo.getId(), photo.getVehicleId(), photo.getOriginalFilename(),
                photo.getMediaType(), photo.getSizeBytes(), photo.getWidthPixels(), photo.getHeightPixels(),
                photo.getCaption(), photo.getCreatedAt(), VehicleProfileField.Source.USER_PROVIDED,
                "/api/vehicles/" + photo.getVehicleId() + "/photos/" + photo.getId() + "/content");
    }

    private static String encodeFilename(String filename) {
        StringBuilder result = new StringBuilder();
        for (byte value : filename.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = value & 0xff;
            if (unsigned >= 'a' && unsigned <= 'z' || unsigned >= 'A' && unsigned <= 'Z'
                    || unsigned >= '0' && unsigned <= '9' || unsigned == '.' || unsigned == '-' || unsigned == '_') {
                result.append((char) unsigned);
            } else {
                result.append('%').append(String.format("%02X", unsigned));
            }
        }
        return result.toString();
    }
}
