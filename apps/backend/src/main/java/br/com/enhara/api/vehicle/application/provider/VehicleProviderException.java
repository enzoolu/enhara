package br.com.enhara.api.vehicle.application.provider;

public class VehicleProviderException extends RuntimeException {
    public VehicleProviderException(String message) {
        super(message);
    }

    public VehicleProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
