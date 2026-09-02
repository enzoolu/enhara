package br.com.enhara.api.vehicle.infrastructure;

import br.com.enhara.api.vehicle.application.VehiclePhotoStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class FileSystemVehiclePhotoStorage implements VehiclePhotoStorage {
    private final Path root;

    public FileSystemVehiclePhotoStorage(@Value("${enhara.vehicle-photos.directory:.data/vehicle-photos}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(root);
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível armazenar a foto do veículo.", exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler a foto do veículo.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível remover a foto do veículo.", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (!storageKey.matches("^[a-f0-9-]{36}\\.(jpg|png)$")) {
            throw new IllegalArgumentException("Identificador de foto inválido.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Identificador de foto inválido.");
        return resolved;
    }
}
