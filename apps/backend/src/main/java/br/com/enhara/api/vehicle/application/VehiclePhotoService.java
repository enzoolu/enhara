package br.com.enhara.api.vehicle.application;

import br.com.enhara.api.shared.error.ResourceNotFoundException;
import br.com.enhara.api.vehicle.domain.VehiclePhoto;
import br.com.enhara.api.vehicle.infrastructure.VehiclePhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VehiclePhotoService {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 12_000;
    private static final long MAX_PIXELS = 40_000_000L;

    private final VehicleService vehicles;
    private final VehiclePhotoRepository photos;
    private final VehiclePhotoStorage storage;

    public VehiclePhotoService(VehicleService vehicles, VehiclePhotoRepository photos, VehiclePhotoStorage storage) {
        this.vehicles = vehicles;
        this.photos = photos;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<VehiclePhoto> list(UUID vehicleId) {
        vehicles.get(vehicleId);
        return photos.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
    }

    @Transactional
    public VehiclePhoto create(UUID vehicleId, MultipartFile file, String caption) {
        vehicles.get(vehicleId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione uma foto.");
        if (file.getSize() > MAX_SIZE_BYTES) throw new IllegalArgumentException("A foto deve ter no máximo 5 MB.");
        if (caption != null && caption.trim().length() > 240) {
            throw new IllegalArgumentException("A legenda deve ter no máximo 240 caracteres.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível processar a foto.", exception);
        }
        Image image = inspect(content);
        String storageKey = UUID.randomUUID() + image.extension();
        storage.store(storageKey, content);
        try {
            String originalName = sanitizeFilename(file.getOriginalFilename(), image.extension());
            return photos.save(new VehiclePhoto(vehicleId, originalName, image.mediaType(), storageKey,
                    content.length, image.width(), image.height(), caption, Instant.now()));
        } catch (RuntimeException exception) {
            storage.delete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PhotoContent content(UUID vehicleId, UUID photoId) {
        VehiclePhoto photo = getForVehicle(vehicleId, photoId);
        return new PhotoContent(photo, storage.read(photo.getStorageKey()));
    }

    @Transactional
    public void delete(UUID vehicleId, UUID photoId) {
        VehiclePhoto photo = getForVehicle(vehicleId, photoId);
        storage.delete(photo.getStorageKey());
        photos.delete(photo);
    }

    private VehiclePhoto getForVehicle(UUID vehicleId, UUID photoId) {
        vehicles.get(vehicleId);
        return photos.findById(photoId)
                .filter(photo -> photo.getVehicleId().equals(vehicleId))
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada: " + photoId));
    }

    private static Image inspect(byte[] content) {
        String format;
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P'
                && content[2] == 'N' && content[3] == 'G') {
            format = "png";
        } else if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            format = "jpeg";
        } else {
            throw new IllegalArgumentException("Use uma imagem JPEG ou PNG válida.");
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
            if (decoded == null) throw new IllegalArgumentException("A imagem está corrompida ou não é suportada.");
            int width = decoded.getWidth();
            int height = decoded.getHeight();
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) {
                throw new IllegalArgumentException("As dimensões da imagem não são suportadas.");
            }
            return "png".equals(format)
                    ? new Image("image/png", ".png", width, height)
                    : new Image("image/jpeg", ".jpg", width, height);
        } catch (IOException exception) {
            throw new IllegalArgumentException("A imagem está corrompida ou não é suportada.", exception);
        }
    }

    private static String sanitizeFilename(String rawName, String extension) {
        String name = rawName == null ? "foto" + extension : rawName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank()) name = "foto" + extension;
        if (name.length() > 255) name = name.substring(0, 240) + extension;
        return name;
    }

    private record Image(String mediaType, String extension, int width, int height) {
    }

    public record PhotoContent(VehiclePhoto metadata, byte[] bytes) {
    }
}
