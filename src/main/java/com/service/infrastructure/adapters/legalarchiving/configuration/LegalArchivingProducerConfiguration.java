package com.service.infrastructure.adapters.legalarchiving.configuration;

import com.service.infrastructure.configuration.NativeKafkaProducerPropertiesFactory;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Bridges global Kafka settings and legal-archiving feature settings.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LegalArchivingProducerConfiguration {

    private final NativeKafkaProducerPropertiesFactory producerPropertiesFactory;
    private final LegalArchivingFeatureConfig featureConfig;

    /**
     * @return {@code true} when both Kafka infrastructure and the legal-archiving feature are enabled
     */
    public boolean enabled() {
        return producerPropertiesFactory.infrastructureEnabled() && featureConfig.enabled();
    }

    /**
     * @return the Kafka topic dedicated to legal archiving
     */
    public String topic() {
        return featureConfig.topic();
    }

    /**
     * Builds the effective producer properties, including serializer choices for the simulated contract.
     *
     * @return Kafka producer properties ready to instantiate a producer
     */
    public Properties toProducerProperties() {
        Properties properties = producerPropertiesFactory.createProducerProperties(featureConfig.producerClientIdSuffix());
        properties.put(ProducerConfig.ACKS_CONFIG, featureConfig.producer().acks());
        properties.put(ProducerConfig.RETRIES_CONFIG, Integer.toString(featureConfig.producer().retries()));
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                Integer.toString(featureConfig.producer().requestTimeoutMs()));
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                Boolean.toString(featureConfig.producer().enableIdempotence()));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return properties;
    }
}
