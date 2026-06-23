package com.service.infrastructure.adapters.legalarchiving.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.Config;

/**
 * Extracts Kafka client properties dedicated to legal archiving from the feature namespace.
 *
 * <p>Properties declared under
 * {@code agw.connectivity.features.legal-archiving.kafka.*} are forwarded as native Kafka client
 * properties after stripping that prefix. This lets legal archiving override the default broker
 * connection only when needed, while still inheriting the shared AGR Kafka settings when no
 * feature-specific value is configured.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LegalArchivingKafkaPropertiesProvider {

    static final String PROPERTY_PREFIX = "agw.connectivity.features.legal-archiving.kafka.";

    private final Config config;

    /**
     * @return Kafka properties explicitly configured for legal archiving
     */
    public Map<String, Object> overrideProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String propertyName : config.getPropertyNames()) {
            if (!propertyName.startsWith(PROPERTY_PREFIX)) {
                continue;
            }

            String kafkaPropertyName = propertyName.substring(PROPERTY_PREFIX.length());
            if (kafkaPropertyName.isBlank()) {
                continue;
            }

            config.getOptionalValue(propertyName, String.class)
                    .filter(value -> !value.isBlank())
                    .ifPresent(value -> properties.put(kafkaPropertyName, value));
        }
        return properties;
    }
}
