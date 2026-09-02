package br.com.enhara.api.vehicle.application;

import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import br.com.enhara.api.vehicle.domain.VehicleProviderCacheEntry;
import br.com.enhara.api.vehicle.domain.VehicleProviderStatus;
import br.com.enhara.api.vehicle.infrastructure.VehicleProviderCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class VehicleProviderCacheService {
    public record Resolution(VehicleDataProvider.Data data, VehicleProviderStatus.State state, String message,
                             Instant fetchedAt, Instant expiresAt, boolean cached, boolean stale) {
    }

    private record CachePayload(Map<VehicleProfileField.Key, String> fields, String sourceUrl) {
    }

    private final VehicleProviderCacheRepository cache;
    private final ObjectMapper objectMapper;

    public VehicleProviderCacheService(VehicleProviderCacheRepository cache) {
        this.cache = cache;
        this.objectMapper = new ObjectMapper();
    }

    public Resolution resolve(VehicleDataProvider provider, VehicleDataProvider.Lookup lookup, boolean forceRefresh) {
        Instant now = Instant.now();
        var cached = cache.findByProviderAndLookupKey(provider.id(), lookup.cacheKey());
        if (!forceRefresh && cached.filter(entry -> entry.getExpiresAt().isAfter(now)).isPresent()) {
            VehicleProviderCacheEntry entry = cached.orElseThrow();
            return fromCache(entry, VehicleProviderStatus.State.CACHE_FRESH,
                    "Dados válidos reutilizados do cache local.", false);
        }

        try {
            VehicleDataProvider.Data data = provider.fetch(lookup);
            Instant expiresAt = now.plus(provider.cacheTtl());
            String payload = writePayload(data);
            VehicleProviderCacheEntry entry = cached.orElseGet(() ->
                    new VehicleProviderCacheEntry(provider.id(), lookup.cacheKey(), payload, now, expiresAt));
            entry.update(payload, now, expiresAt);
            cache.save(entry);
            return new Resolution(data, VehicleProviderStatus.State.LIVE,
                    "Dados atualizados diretamente pelo provider.", now, expiresAt, false, false);
        } catch (Exception exception) {
            if (cached.isPresent()) {
                VehicleProviderCacheEntry entry = cached.orElseThrow();
                boolean stale = !entry.getExpiresAt().isAfter(now);
                return fromCache(entry,
                        stale ? VehicleProviderStatus.State.CACHE_STALE : VehicleProviderStatus.State.CACHE_FRESH,
                        stale
                                ? "Provider indisponível; usando o último dado armazenado, fora da validade do cache."
                                : "Provider indisponível; usando dado armazenado ainda válido.",
                        stale);
            }
            return new Resolution(null, VehicleProviderStatus.State.UNAVAILABLE,
                    safeMessage(exception), null, null, false, false);
        }
    }

    private Resolution fromCache(VehicleProviderCacheEntry entry, VehicleProviderStatus.State state,
                                 String message, boolean stale) {
        CachePayload payload = readPayload(entry.getPayloadJson());
        return new Resolution(new VehicleDataProvider.Data(payload.fields(), payload.sourceUrl()), state, message,
                entry.getFetchedAt(), entry.getExpiresAt(), true, stale);
    }

    private String writePayload(VehicleDataProvider.Data data) {
        try {
            return objectMapper.writeValueAsString(new CachePayload(data.fields(), data.sourceUrl()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar cache de provider", exception);
        }
    }

    private CachePayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, CachePayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cache de provider inválido", exception);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Provider indisponível no momento." : message;
    }
}
