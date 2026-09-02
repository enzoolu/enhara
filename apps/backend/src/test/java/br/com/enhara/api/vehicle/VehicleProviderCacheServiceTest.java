package br.com.enhara.api.vehicle;

import br.com.enhara.api.vehicle.application.VehicleProviderCacheService;
import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider;
import br.com.enhara.api.vehicle.application.provider.VehicleProviderException;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import br.com.enhara.api.vehicle.domain.VehicleProviderCacheEntry;
import br.com.enhara.api.vehicle.domain.VehicleProviderStatus;
import br.com.enhara.api.vehicle.infrastructure.VehicleProviderCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleProviderCacheServiceTest {
    @Test
    void storesSuccessfulProviderResult() {
        VehicleProviderCacheRepository repository = mock(VehicleProviderCacheRepository.class);
        when(repository.findByProviderAndLookupKey("TEST", "VIN:VALUE:")).thenReturn(Optional.empty());
        VehicleProviderCacheService service = new VehicleProviderCacheService(repository);

        var result = service.resolve(new Provider(false),
                new VehicleDataProvider.VinLookup("value", null), false);

        assertThat(result.state()).isEqualTo(VehicleProviderStatus.State.LIVE);
        assertThat(result.data().fields()).containsEntry(VehicleProfileField.Key.MODEL, "Modelo real");
        verify(repository).save(any(VehicleProviderCacheEntry.class));
    }

    @Test
    void returnsStoredDataWhenProviderIsOfflineEvenAfterCacheExpiry() {
        VehicleProviderCacheRepository repository = mock(VehicleProviderCacheRepository.class);
        String payload = "{\"fields\":{\"MODEL\":\"Último modelo\"},\"sourceUrl\":\"https://provider.test\"}";
        VehicleProviderCacheEntry entry = new VehicleProviderCacheEntry("TEST", "VIN:VALUE:", payload,
                Instant.now().minus(Duration.ofDays(2)), Instant.now().minus(Duration.ofDays(1)));
        when(repository.findByProviderAndLookupKey("TEST", "VIN:VALUE:")).thenReturn(Optional.of(entry));
        VehicleProviderCacheService service = new VehicleProviderCacheService(repository);

        var result = service.resolve(new Provider(true),
                new VehicleDataProvider.VinLookup("value", null), true);

        assertThat(result.state()).isEqualTo(VehicleProviderStatus.State.CACHE_STALE);
        assertThat(result.stale()).isTrue();
        assertThat(result.fetchedAt()).isEqualTo(entry.getFetchedAt());
        assertThat(result.data().fields()).containsEntry(VehicleProfileField.Key.MODEL, "Último modelo");
    }

    private record Provider(boolean offline) implements VehicleDataProvider {
        @Override public String id() { return "TEST"; }
        @Override public boolean supports(Lookup lookup) { return true; }
        @Override public Duration cacheTtl() { return Duration.ofDays(1); }
        @Override public Data fetch(Lookup lookup) {
            if (offline) throw new VehicleProviderException("offline");
            return new Data(Map.of(VehicleProfileField.Key.MODEL, "Modelo real"), "https://provider.test");
        }
    }
}
