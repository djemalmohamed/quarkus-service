package com.service.infrastructure.adapters.legalarchiving.configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces the dedicated worker pool used to isolate legal-archiving transport offload from the
 * shared reactive worker infrastructure.
 *
 * <p>The native Kafka producer can occasionally block while obtaining metadata, opening the first
 * broker connection, or reacting to transport back-pressure. Offloading that subscription work to
 * a feature-specific bounded pool prevents those transient stalls from impacting unrelated reactive
 * flows.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LegalArchivingWorkerPoolProducer {

    private final LegalArchivingFeatureConfig featureConfig;
    private ExecutorService executorService;
    private long shutdownTimeoutMillis;

    @PostConstruct
    void init() {
        LegalArchivingFeatureConfig.Worker workerConfig = featureConfig.worker();
        int poolSize = workerConfig.poolSize();
        int queueSize = workerConfig.queueSize();
        shutdownTimeoutMillis = workerConfig.shutdownTimeout().toMillis();
        validateConfiguration(poolSize, queueSize, shutdownTimeoutMillis);
        executorService = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new LegalArchivingThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        log.info(
                "Legal archiving worker pool initialized poolSize={} queueSize={} shutdownTimeoutMs={}",
                poolSize,
                queueSize,
                shutdownTimeoutMillis);
    }

    /**
     * Exposes the dedicated executor to legal-archiving transport components.
     *
     * @return application-scoped executor reserved for legal-archiving offload
     */
    @Produces
    @ApplicationScoped
    @LegalArchivingWorker
    Executor executor() {
        return executorService;
    }

    /**
     * Shuts down the dedicated worker pool gracefully and interrupts lingering tasks when needed.
     */
    @PreDestroy
    void close() {
        if (null == executorService) {
            return;
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void validateConfiguration(int poolSize, int queueSize, long shutdownTimeoutMillis) {
        if (poolSize < 1) {
            throw new IllegalStateException("Legal archiving worker.pool-size must be greater than zero");
        }
        if (queueSize < 1) {
            throw new IllegalStateException("Legal archiving worker.queue-size must be greater than zero");
        }
        if (shutdownTimeoutMillis < 1) {
            throw new IllegalStateException("Legal archiving worker.shutdown-timeout must be greater than zero");
        }
    }

    private static final class LegalArchivingThreadFactory implements ThreadFactory {

        private final AtomicInteger threadCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "legal-archiving-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
