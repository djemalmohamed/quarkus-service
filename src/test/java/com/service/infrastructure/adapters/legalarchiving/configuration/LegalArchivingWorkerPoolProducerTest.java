package com.service.infrastructure.adapters.legalarchiving.configuration;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalArchivingWorkerPoolProducerTest {

    @Test
    void shouldCreateBoundedExecutorUsingConfiguredWorkerSettings() {
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.Worker workerConfig = mock(LegalArchivingFeatureConfig.Worker.class);
        when(featureConfig.worker()).thenReturn(workerConfig);
        when(workerConfig.poolSize()).thenReturn(3);
        when(workerConfig.queueSize()).thenReturn(12);
        when(workerConfig.shutdownTimeout()).thenReturn(Duration.ofSeconds(5));

        LegalArchivingWorkerPoolProducer producer = new LegalArchivingWorkerPoolProducer(featureConfig);
        producer.init();
        try {
            Executor executor = producer.executor();
            ThreadPoolExecutor threadPoolExecutor = assertInstanceOf(ThreadPoolExecutor.class, executor);
            assertEquals(3, threadPoolExecutor.getCorePoolSize());
            assertEquals(3, threadPoolExecutor.getMaximumPoolSize());
            assertEquals(12, threadPoolExecutor.getQueue().remainingCapacity());
        } finally {
            producer.close();
        }
    }

    @Test
    void shouldShutdownExecutorWhenProducerCloses() {
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.Worker workerConfig = mock(LegalArchivingFeatureConfig.Worker.class);
        when(featureConfig.worker()).thenReturn(workerConfig);
        when(workerConfig.poolSize()).thenReturn(1);
        when(workerConfig.queueSize()).thenReturn(1);
        when(workerConfig.shutdownTimeout()).thenReturn(Duration.ofSeconds(1));

        LegalArchivingWorkerPoolProducer producer = new LegalArchivingWorkerPoolProducer(featureConfig);
        producer.init();
        ThreadPoolExecutor executor = assertInstanceOf(ThreadPoolExecutor.class, producer.executor());

        producer.close();

        assertTrue(executor.isShutdown());
    }

    @Test
    void shouldRejectNonPositivePoolSize() {
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.Worker workerConfig = mock(LegalArchivingFeatureConfig.Worker.class);
        when(featureConfig.worker()).thenReturn(workerConfig);
        when(workerConfig.poolSize()).thenReturn(0);
        when(workerConfig.queueSize()).thenReturn(10);
        when(workerConfig.shutdownTimeout()).thenReturn(Duration.ofSeconds(1));

        LegalArchivingWorkerPoolProducer producer = new LegalArchivingWorkerPoolProducer(featureConfig);

        assertThrows(IllegalStateException.class, producer::init);
    }

    @Test
    void shouldRejectNonPositiveQueueSize() {
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.Worker workerConfig = mock(LegalArchivingFeatureConfig.Worker.class);
        when(featureConfig.worker()).thenReturn(workerConfig);
        when(workerConfig.poolSize()).thenReturn(2);
        when(workerConfig.queueSize()).thenReturn(0);
        when(workerConfig.shutdownTimeout()).thenReturn(Duration.ofSeconds(1));

        LegalArchivingWorkerPoolProducer producer = new LegalArchivingWorkerPoolProducer(featureConfig);

        assertThrows(IllegalStateException.class, producer::init);
    }

    @Test
    void shouldRejectNonPositiveShutdownTimeout() {
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.Worker workerConfig = mock(LegalArchivingFeatureConfig.Worker.class);
        when(featureConfig.worker()).thenReturn(workerConfig);
        when(workerConfig.poolSize()).thenReturn(2);
        when(workerConfig.queueSize()).thenReturn(10);
        when(workerConfig.shutdownTimeout()).thenReturn(Duration.ZERO);

        LegalArchivingWorkerPoolProducer producer = new LegalArchivingWorkerPoolProducer(featureConfig);

        assertThrows(IllegalStateException.class, producer::init);
    }
}
