package br.com.enhara.api.realtime.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseHub {
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID vehicleId) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> vehicleEmitters = emitters.computeIfAbsent(vehicleId, ignored -> ConcurrentHashMap.newKeySet());
        vehicleEmitters.add(emitter);
        Runnable cleanup = () -> remove(vehicleId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("vehicleId", vehicleId, "connectedAt", Instant.now())));
        } catch (IOException exception) {
            remove(vehicleId, emitter);
        }
        return emitter;
    }

    public void publish(UUID vehicleId, String eventName, Object payload) {
        Set<SseEmitter> vehicleEmitters = emitters.get(vehicleId);
        if (vehicleEmitters == null) {
            return;
        }
        vehicleEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException exception) {
                remove(vehicleId, emitter);
            }
        });
    }

    /** Ensures clients only observe state that has already been committed. */
    public void publishAfterCommit(UUID vehicleId, String eventName, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(vehicleId, eventName, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(vehicleId, eventName, payload);
            }
        });
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach((vehicleId, vehicleEmitters) -> vehicleEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException exception) {
                remove(vehicleId, emitter);
            }
        }));
    }

    private void remove(UUID vehicleId, SseEmitter emitter) {
        Set<SseEmitter> vehicleEmitters = emitters.get(vehicleId);
        if (vehicleEmitters != null) {
            vehicleEmitters.remove(emitter);
            if (vehicleEmitters.isEmpty()) {
                emitters.remove(vehicleId, vehicleEmitters);
            }
        }
    }
}
