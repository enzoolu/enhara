package br.com.enhara.api.vehicle.infrastructure;

import br.com.enhara.api.vehicle.domain.VehicleProviderCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VehicleProviderCacheRepository extends JpaRepository<VehicleProviderCacheEntry, UUID> {
    Optional<VehicleProviderCacheEntry> findByProviderAndLookupKey(String provider, String lookupKey);
}
