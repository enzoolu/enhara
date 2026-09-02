package br.com.enhara.api.vehicle.application;

import br.com.enhara.api.shared.error.ResourceNotFoundException;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.ConfirmProfileRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.EcuVinRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.EnrichProfileRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.ManualProfileRequest;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.ProfileFieldResponse;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.ProvenanceResponse;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.ProviderStatusResponse;
import br.com.enhara.api.vehicle.api.VehicleProfileModels.VehicleProfileResponse;
import br.com.enhara.api.vehicle.application.provider.VehicleDataProvider;
import br.com.enhara.api.vehicle.domain.Vehicle;
import br.com.enhara.api.vehicle.domain.VehicleProfileField;
import br.com.enhara.api.vehicle.domain.VehicleProviderStatus;
import br.com.enhara.api.vehicle.infrastructure.VehicleProfileFieldRepository;
import br.com.enhara.api.vehicle.infrastructure.VehicleProviderStatusRepository;
import br.com.enhara.api.vehicle.infrastructure.provider.BrasilApiFipeVehicleDataProvider;
import br.com.enhara.api.vehicle.infrastructure.provider.NhtsaVpicVehicleDataProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class VehicleProfileService {
    private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    private static final Pattern FIPE_CODE = Pattern.compile("^[0-9]{6}-[0-9]$");

    private final VehicleService vehicles;
    private final VehicleProfileFieldRepository fields;
    private final VehicleProviderStatusRepository statuses;
    private final VehicleProviderCacheService cache;
    private final Map<String, VehicleDataProvider> providers;

    public VehicleProfileService(VehicleService vehicles, VehicleProfileFieldRepository fields,
                                 VehicleProviderStatusRepository statuses, VehicleProviderCacheService cache,
                                 List<VehicleDataProvider> providers) {
        this.vehicles = vehicles;
        this.fields = fields;
        this.statuses = statuses;
        this.cache = cache;
        this.providers = new HashMap<>();
        providers.forEach(provider -> this.providers.put(provider.id(), provider));
    }

    @Transactional(readOnly = true)
    public VehicleProfileResponse get(UUID vehicleId) {
        Vehicle vehicle = vehicles.get(vehicleId);
        List<VehicleProviderStatus> providerStatuses = statuses.findByVehicleIdOrderByProviderAsc(vehicleId);
        Map<String, VehicleProviderStatus.State> stateByProvider = new HashMap<>();
        providerStatuses.forEach(status -> stateByProvider.put(status.getProvider(), status.getState()));

        Map<VehicleProfileField.Key, ProfileFieldResponse> result = registrationFields(vehicle);
        Instant updatedAt = vehicle.getCreatedAt();
        for (VehicleProfileField field : fields.findByVehicleIdOrderByKeyAsc(vehicleId)) {
            result.put(field.getKey(), response(field, stateByProvider.get(field.getProvider())));
            if (field.getRetrievedAt().isAfter(updatedAt)) updatedAt = field.getRetrievedAt();
        }
        return new VehicleProfileResponse(vehicleId,
                result.values().stream().sorted(Comparator.comparing(item -> item.key().ordinal())).toList(),
                providerStatuses.stream().map(ProviderStatusResponse::from).toList(), updatedAt);
    }

    @Transactional
    public VehicleProfileResponse updateManual(UUID vehicleId, ManualProfileRequest request) {
        vehicles.get(vehicleId);
        Instant now = Instant.now();
        request.fields().forEach((key, rawValue) -> upsertManual(vehicleId, key, validate(key, rawValue), now));
        return get(vehicleId);
    }

    @Transactional
    public VehicleProfileResponse confirm(UUID vehicleId, ConfirmProfileRequest request) {
        vehicles.get(vehicleId);
        Instant now = Instant.now();
        for (VehicleProfileField.Key key : request.fields()) {
            VehicleProfileField field = fields.findByVehicleIdAndKey(vehicleId, key)
                    .orElseThrow(() -> new ResourceNotFoundException("Campo do perfil não encontrado: " + key));
            field.confirm(now);
        }
        return get(vehicleId);
    }

    @Transactional
    public VehicleProfileResponse recordRealEcuVin(UUID vehicleId, EcuVinRequest request) {
        vehicles.get(vehicleId);
        String vin = validate(VehicleProfileField.Key.VIN, request.vin());
        Instant now = Instant.now();
        VehicleProfileField existing = fields.findByVehicleIdAndKey(vehicleId, VehicleProfileField.Key.VIN).orElse(null);
        if (existing == null) {
            fields.save(new VehicleProfileField(vehicleId, VehicleProfileField.Key.VIN, vin,
                    VehicleProfileField.Source.ECU_OBD, null, null,
                    request.observedAt() == null ? now : request.observedAt(), now, null, null));
        } else if (!existing.isUserControlled()) {
            existing.replace(vin, VehicleProfileField.Source.ECU_OBD, null, null,
                    request.observedAt() == null ? now : request.observedAt(), now, null, null);
        }
        return get(vehicleId);
    }

    @Transactional
    public VehicleProfileResponse enrich(UUID vehicleId, EnrichProfileRequest request) {
        Vehicle vehicle = vehicles.get(vehicleId);
        Instant now = Instant.now();
        boolean hasFipeCode = request.fipeCode() != null && !request.fipeCode().isBlank();
        boolean hasFipeSelection = request.fipeSelection() != null;
        if (hasFipeCode && hasFipeSelection) {
            throw new IllegalArgumentException("Informe o código FIPE ou use a seleção guiada, não os dois.");
        }
        if (hasFipeCode) {
            upsertManual(vehicleId, VehicleProfileField.Key.FIPE_CODE,
                    validate(VehicleProfileField.Key.FIPE_CODE, request.fipeCode()), now);
        }

        Map<VehicleProfileField.Key, VehicleProfileField> current = persistedFields(vehicleId);
        Integer modelYear = profileModelYear(current, vehicle);
        VehicleDataProvider fipeProvider = requiredProvider(BrasilApiFipeVehicleDataProvider.ID);
        if (hasFipeSelection) {
            var selection = request.fipeSelection();
            resolve(vehicleId, fipeProvider,
                    new VehicleDataProvider.FipeSelectionLookup(selection.vehicleType(), selection.brandCode(),
                            selection.modelCode(), selection.yearCode()),
                    request.forceRefresh(), current);
        } else {
            String fipeCode = value(current, VehicleProfileField.Key.FIPE_CODE);
            if (fipeCode != null) {
                resolve(vehicleId, fipeProvider,
                        new VehicleDataProvider.FipeCodeLookup(fipeCode, modelYear),
                        request.forceRefresh(), current);
            } else {
                updateStatus(vehicleId, BrasilApiFipeVehicleDataProvider.ID,
                        VehicleProviderStatus.State.NOT_REQUESTED,
                        "Escolha marca, modelo e ano/combustível ou informe um código FIPE.", now, null);
            }
        }

        current = persistedFields(vehicleId);
        String vin = value(current, VehicleProfileField.Key.VIN);
        if (vin == null) vin = vehicle.getVin();
        if (vin != null && VIN.matcher(vin).matches()) {
            resolve(vehicleId, requiredProvider(NhtsaVpicVehicleDataProvider.ID),
                    new VehicleDataProvider.VinLookup(vin, modelYear),
                    request.forceRefresh(), current);
        } else {
            updateStatus(vehicleId, NhtsaVpicVehicleDataProvider.ID, VehicleProviderStatus.State.NOT_REQUESTED,
                    "Nenhum VIN válido está disponível para consulta.", now, null);
        }
        return get(vehicleId);
    }

    private void resolve(UUID vehicleId, VehicleDataProvider provider, VehicleDataProvider.Lookup lookup,
                         boolean forceRefresh, Map<VehicleProfileField.Key, VehicleProfileField> current) {
        if (!provider.supports(lookup)) {
            throw new IllegalArgumentException("O provider " + provider.id() + " não suporta esta consulta.");
        }
        VehicleProviderCacheService.Resolution resolution = cache.resolve(provider, lookup, forceRefresh);
        Instant now = Instant.now();
        updateStatus(vehicleId, provider.id(), resolution.state(), resolution.message(), now, resolution.fetchedAt());
        if (resolution.data() == null) return;
        if (provider.id().equals(NhtsaVpicVehicleDataProvider.ID)
                && !identityMatches(current, resolution.data().fields())) {
            updateStatus(vehicleId, provider.id(), VehicleProviderStatus.State.CONFLICT,
                    "O VIN retornou fabricante/modelo incompatível com o perfil confirmado; os dados foram ignorados.",
                    now, resolution.fetchedAt());
            return;
        }
        VehicleProfileField.Source source = sourceFor(provider.id());
        resolution.data().fields().forEach((key, value) -> {
            VehicleProfileField existing = current.get(key);
            boolean explicitlySelectedFipeCode = lookup instanceof VehicleDataProvider.FipeSelectionLookup
                    && key == VehicleProfileField.Key.FIPE_CODE;
            if (existing != null && !explicitlySelectedFipeCode && (existing.isUserControlled()
                    || priority(existing.getSource()) > priority(source))) return;
            if (existing == null) {
                existing = new VehicleProfileField(vehicleId, key, value, source, provider.id(),
                        resolution.data().sourceUrl(), null, resolution.fetchedAt(), resolution.expiresAt(), null);
                fields.save(existing);
                current.put(key, existing);
            } else {
                existing.replace(value, source, provider.id(), resolution.data().sourceUrl(), null,
                        resolution.fetchedAt(), resolution.expiresAt(), null);
            }
        });
    }

    private void updateStatus(UUID vehicleId, String provider, VehicleProviderStatus.State state, String message,
                              Instant checkedAt, Instant dataFetchedAt) {
        VehicleProviderStatus status = statuses.findByVehicleIdAndProvider(vehicleId, provider)
                .orElseGet(() -> new VehicleProviderStatus(vehicleId, provider, state, message, checkedAt, dataFetchedAt));
        status.update(state, message, checkedAt, dataFetchedAt);
        statuses.save(status);
    }

    private void upsertManual(UUID vehicleId, VehicleProfileField.Key key, String value, Instant now) {
        VehicleProfileField field = fields.findByVehicleIdAndKey(vehicleId, key).orElse(null);
        if (field == null) {
            fields.save(new VehicleProfileField(vehicleId, key, value, VehicleProfileField.Source.USER_PROVIDED,
                    null, null, null, now, null, now));
        } else {
            field.replace(value, VehicleProfileField.Source.USER_PROVIDED, null, null, null, now, null, now);
        }
    }

    private Map<VehicleProfileField.Key, VehicleProfileField> persistedFields(UUID vehicleId) {
        Map<VehicleProfileField.Key, VehicleProfileField> result = new EnumMap<>(VehicleProfileField.Key.class);
        fields.findByVehicleIdOrderByKeyAsc(vehicleId).forEach(field -> result.put(field.getKey(), field));
        return result;
    }

    private static Map<VehicleProfileField.Key, ProfileFieldResponse> registrationFields(Vehicle vehicle) {
        Map<VehicleProfileField.Key, ProfileFieldResponse> result = new EnumMap<>(VehicleProfileField.Key.class);
        if (vehicle.getVin() != null && !vehicle.getVin().isBlank()) {
            putRegistration(result, VehicleProfileField.Key.VIN, vehicle.getVin(), vehicle.getCreatedAt());
        }
        putRegistration(result, VehicleProfileField.Key.MANUFACTURER, vehicle.getManufacturer(), vehicle.getCreatedAt());
        putRegistration(result, VehicleProfileField.Key.MODEL, vehicle.getModel(), vehicle.getCreatedAt());
        putRegistration(result, VehicleProfileField.Key.MODEL_YEAR, String.valueOf(vehicle.getYear()), vehicle.getCreatedAt());
        return result;
    }

    private static void putRegistration(Map<VehicleProfileField.Key, ProfileFieldResponse> result,
                                        VehicleProfileField.Key key, String value, Instant retrievedAt) {
        result.put(key, new ProfileFieldResponse(key, value,
                new ProvenanceResponse(VehicleProfileField.Source.VEHICLE_REGISTRATION, null, null, null,
                        retrievedAt, null, false, false, null)));
    }

    private static ProfileFieldResponse response(VehicleProfileField field, VehicleProviderStatus.State providerState) {
        boolean cached = providerState == VehicleProviderStatus.State.CACHE_FRESH
                || providerState == VehicleProviderStatus.State.CACHE_STALE;
        boolean stale = providerState == VehicleProviderStatus.State.CACHE_STALE
                || field.getProviderExpiresAt() != null && field.getProviderExpiresAt().isBefore(Instant.now());
        return new ProfileFieldResponse(field.getKey(), field.getValue(),
                new ProvenanceResponse(field.getSource(), field.getProvider(), field.getSourceUrl(),
                        field.getObservedAt(), field.getRetrievedAt(), field.getProviderExpiresAt(), cached, stale,
                        field.getConfirmedAt()));
    }

    private static Integer profileModelYear(Map<VehicleProfileField.Key, VehicleProfileField> current, Vehicle vehicle) {
        String value = value(current, VehicleProfileField.Key.MODEL_YEAR);
        if (value == null) return vehicle.getYear();
        try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { return vehicle.getYear(); }
    }

    private static String value(Map<VehicleProfileField.Key, VehicleProfileField> current, VehicleProfileField.Key key) {
        VehicleProfileField field = current.get(key);
        return field == null ? null : field.getValue();
    }

    private static boolean identityMatches(Map<VehicleProfileField.Key, VehicleProfileField> current,
                                           Map<VehicleProfileField.Key, String> candidate) {
        return fieldMatches(current, candidate, VehicleProfileField.Key.MANUFACTURER)
                && fieldMatches(current, candidate, VehicleProfileField.Key.MODEL);
    }

    private static boolean fieldMatches(Map<VehicleProfileField.Key, VehicleProfileField> current,
                                        Map<VehicleProfileField.Key, String> candidate,
                                        VehicleProfileField.Key key) {
        String expected = value(current, key);
        String returned = candidate.get(key);
        if (expected == null || returned == null) return true;
        String left = normalizeIdentity(expected);
        String right = normalizeIdentity(returned);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private static String normalizeIdentity(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private VehicleDataProvider requiredProvider(String id) {
        VehicleDataProvider provider = providers.get(id);
        if (provider == null) throw new IllegalStateException("Provider não configurado: " + id);
        return provider;
    }

    private static VehicleProfileField.Source sourceFor(String provider) {
        return switch (provider) {
            case BrasilApiFipeVehicleDataProvider.ID -> VehicleProfileField.Source.BRASILAPI_FIPE;
            case NhtsaVpicVehicleDataProvider.ID -> VehicleProfileField.Source.NHTSA_VPIC;
            default -> throw new IllegalArgumentException("Provider sem provenance: " + provider);
        };
    }

    private static int priority(VehicleProfileField.Source source) {
        return switch (source) {
            case USER_PROVIDED -> 5;
            case ECU_OBD -> 4;
            case BRASILAPI_FIPE -> 3;
            case NHTSA_VPIC -> 2;
            case VEHICLE_REGISTRATION -> 1;
        };
    }

    private static String validate(VehicleProfileField.Key key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("O valor de " + key + " não pode ser vazio.");
        if (value.length() > 512) throw new IllegalArgumentException("O valor de " + key + " é muito longo.");
        if (key == VehicleProfileField.Key.VIN) {
            value = value.toUpperCase();
            if (!VIN.matcher(value).matches()) throw new IllegalArgumentException("VIN inválido.");
        }
        if (key == VehicleProfileField.Key.FIPE_CODE && !FIPE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("Código FIPE inválido; use 000000-0.");
        }
        if (key == VehicleProfileField.Key.MODEL_YEAR) {
            try {
                int year = Integer.parseInt(value);
                if (year < 1886 || year > 2100) throw new IllegalArgumentException("Ano do modelo inválido.");
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Ano do modelo inválido.");
            }
        }
        return value;
    }
}
