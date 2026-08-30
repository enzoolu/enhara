package br.com.enhara.api.vehicle.application;

import br.com.enhara.api.vehicle.domain.Vehicle;
import br.com.enhara.api.vehicle.infrastructure.VehicleRepository;
import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.shared.error.ConflictException;
import br.com.enhara.api.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {
    private final VehicleRepository repository;

    public VehicleService(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Vehicle get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo não encontrado: " + id));
    }

    @Transactional
    public Vehicle create(CreateVehicleRequest request) {
        if (repository.existsByVinIgnoreCase(request.vin())) {
            throw new ConflictException("Já existe um veículo com este VIN");
        }
        if (repository.existsByLicensePlateIgnoreCase(request.licensePlate())) {
            throw new ConflictException("Já existe um veículo com esta placa");
        }
        return repository.save(new Vehicle(request.name(), request.vin(), request.manufacturer(), request.model(),
                request.modelYear(), request.licensePlate(), request.odometerKm()));
    }
}
