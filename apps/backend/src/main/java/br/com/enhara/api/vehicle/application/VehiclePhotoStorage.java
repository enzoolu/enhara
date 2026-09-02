package br.com.enhara.api.vehicle.application;

public interface VehiclePhotoStorage {
    void store(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
