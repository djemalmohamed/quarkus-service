package com.service.infrastructure.adapters.legalarchiving.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Feature-level configuration dedicated to the legal-archiving adapter.
 */
@ConfigMapping(prefix = "agw.connectivity.features.legal-archiving")
public interface LegalArchivingFeatureConfig {

    /**
     * @return {@code true} when legal archiving is enabled
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * @return inbound HTTP filtering policy
     */
    @WithName("inbound")
    InboundPolicy inboundPolicy();

    /**
     * @return outbound HTTP filtering policy
     */
    @WithName("outbound")
    OutboundPolicy outboundPolicy();

    /**
     * @return the Kafka topic used for legal archiving
     */
    String topic();

    /**
     * @return the client-id suffix dedicated to the legal-archiving producer
     */
    @WithDefault("legal-archiving")
    String producerClientIdSuffix();

    /**
     * @return producer-specific Kafka overrides for legal archiving only
     */
    Producer producer();

    /**
     * @return dedicated worker-pool settings for legal archiving offload
     */
    Worker worker();

    /**
     * Inbound HTTP policy expressed through method-specific decisions and path overrides.
     */
    interface InboundPolicy {

        /**
         * @return method-specific inbound archiving rules
         */
        Map<String, MethodPolicy> methods();
    }

    /**
     * Outbound HTTP policy expressed through method-specific decisions and path overrides.
     */
    interface OutboundPolicy {

        /**
         * @return method-specific outbound archiving rules
         */
        Map<String, MethodPolicy> methods();
    }

    /**
     * Method-specific legal-archiving behavior.
     */
    interface MethodPolicy {

        /**
         * @return the base decision applied to the configured method
         */
        @WithDefault("ARCHIVE")
        MethodDecision decision();

        /**
         * @return exact paths whose result must invert the configured method decision
         */
        @WithName("override-paths")
        Optional<List<String>> overridePaths();
    }

    /**
     * Producer-specific tuning applied only to the legal-archiving Kafka producer.
     */
    interface Producer {

        /**
         * @return the producer acknowledgement policy, typically {@code all}
         */
        @WithDefault("all")
        String acks();

        /**
         * @return the number of retries allowed before the producer gives up
         */
        @WithDefault("3")
        int retries();

        /**
         * @return the request timeout in milliseconds applied to broker requests
         */
        @WithDefault("15000")
        int requestTimeoutMs();

        /**
         * @return {@code true} when Kafka producer idempotence must be enabled
         */
        @WithDefault("true")
        boolean enableIdempotence();
    }

    /**
     * Dedicated worker-pool settings used to isolate legal-archiving transport offload.
     */
    interface Worker {

        /**
         * @return number of worker threads available for legal-archiving transport work
         */
        @WithDefault("4")
        int poolSize();

        /**
         * @return maximum number of pending legal-archiving tasks kept in memory
         */
        @WithDefault("500")
        int queueSize();

        /**
         * @return graceful shutdown timeout for the dedicated worker pool
         */
        @WithDefault("10S")
        Duration shutdownTimeout();
    }

    /**
     * Base decision configured for one HTTP method.
     */
    enum MethodDecision {
        ARCHIVE,
        SKIP
    }
}
