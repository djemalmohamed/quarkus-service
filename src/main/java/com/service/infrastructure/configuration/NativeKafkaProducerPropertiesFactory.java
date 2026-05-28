package com.service.infrastructure.configuration;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * Produces Kafka producer {@link Properties} for the manually managed native Kafka producer.
 */
@ApplicationScoped
public class NativeKafkaProducerPropertiesFactory {

    private static final String DEFAULT_CLIENT_ID = "agw-connectivity";
    private static final java.util.Set<String> PRODUCER_CONFIG_NAMES = ProducerConfig.configNames();

    private final KafkaConfig kafkaConfig;
    private final Map<String, Object> defaultKafkaBroker;

    @Inject
    public NativeKafkaProducerPropertiesFactory(
            KafkaConfig kafkaConfig,
            @Identifier("default-kafka-broker") Map<String, Object> defaultKafkaBroker) {
        this.kafkaConfig = kafkaConfig;
        this.defaultKafkaBroker = defaultKafkaBroker;
    }

    /**
     * @return {@code true} when the shared Kafka producer infrastructure is active
     */
    public boolean infrastructureEnabled() {
        return kafkaConfig.enabled() && kafkaConfig.producersEnabled();
    }

    /**
     * Builds producer properties shared by feature-specific Kafka publishers.
     *
     * @param clientIdSuffix the suffix to append to the global client-id prefix
     * @return the producer properties to hand to the Kafka client
     */
    public Properties createProducerProperties(String clientIdSuffix) {
        Properties properties = new Properties();
        properties.putAll(copyProducerConfiguration());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, buildClientId(clientIdSuffix));
        return properties;
    }

    /**
     * Derives the legal-archiving client id from the base Kafka client id exposed by Quarkus.
     *
     * @param clientIdSuffix the feature-specific suffix to append
     * @return the final client identifier used by the producer
     */
    private String buildClientId(String clientIdSuffix) {
        Object baseClientId = defaultKafkaBroker.get(ProducerConfig.CLIENT_ID_CONFIG);
        String value = null == baseClientId ? DEFAULT_CLIENT_ID : String.valueOf(baseClientId);
        return null == clientIdSuffix || clientIdSuffix.isBlank()
                ? value
                : value + "-" + clientIdSuffix;
    }

    /**
     * Copies only the properties recognized by Kafka producers from Quarkus' default broker map.
     *
     * @return a producer-ready configuration snapshot derived from {@code default-kafka-broker}
     */
    private Map<String, Object> copyProducerConfiguration() {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : defaultKafkaBroker.entrySet()) {
            if (PRODUCER_CONFIG_NAMES.contains(entry.getKey())) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }
}
