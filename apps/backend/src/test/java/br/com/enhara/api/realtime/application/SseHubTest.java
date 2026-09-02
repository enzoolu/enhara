package br.com.enhara.api.realtime.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class SseHubTest {
    @AfterEach
    void clearTransactionContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publicationWaitsForTransactionCommit() {
        SseHub hub = spy(new SseHub());
        UUID vehicleId = UUID.randomUUID();
        Object payload = new Object();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        hub.publishAfterCommit(vehicleId, "telemetry", payload);

        verify(hub, never()).publish(vehicleId, "telemetry", payload);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        verify(hub).publish(vehicleId, "telemetry", payload);
    }
}
