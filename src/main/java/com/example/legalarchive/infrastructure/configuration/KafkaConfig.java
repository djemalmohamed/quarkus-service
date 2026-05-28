package com.example.legalarchive.infrastructure.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Custom Kafka infrastructure switches kept outside the standard {@code kafka.*} broker properties.
 */
@ConfigMapping(prefix = "agw.connectivity.kafka")
public interface KafkaConfig {

    /**
     * @return {@code true} when Kafka infrastructure is enabled application-wide
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * @return {@code true} when producer-side infrastructure is enabled
     */
    @WithDefault("true")
    boolean producersEnabled();

    /**
     * @return {@code true} when consumer-side infrastructure is enabled
     */
    @WithDefault("true")
    boolean consumersEnabled();
}
