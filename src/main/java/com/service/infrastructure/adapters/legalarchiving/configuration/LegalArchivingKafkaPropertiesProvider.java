package com.service.infrastructure.adapters.legalarchiving.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;

/**
 * Extracts Kafka client properties dedicated to legal archiving from the feature-specific Kafka
 * configuration mapping.
 *
 * <p>This lets legal archiving override the default broker connection only when needed, while
 * still inheriting the shared AGR Kafka settings when no feature-specific value is configured.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LegalArchivingKafkaPropertiesProvider {

    private final LegalArchivingKafkaConfig kafkaConfig;

    /**
     * @return Kafka properties explicitly configured for legal archiving
     */
    public Map<String, Object> overrideProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        putIfPresent(properties, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers());
        putIfPresent(properties, CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaConfig.securityProtocol());
        putIfPresent(properties, SaslConfigs.SASL_MECHANISM, kafkaConfig.saslMechanism());
        putIfPresent(properties, SaslConfigs.SASL_JAAS_CONFIG, kafkaConfig.saslJaasConfig());
        putIfPresent(properties, SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS, kafkaConfig.saslClientCallbackHandlerClass());
        putIfPresent(properties, SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, kafkaConfig.saslLoginCallbackHandlerClass());
        putIfPresent(properties, SaslConfigs.SASL_LOGIN_CLASS, kafkaConfig.saslLoginClass());
        putIfPresent(properties, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaConfig.sslTruststoreLocation());
        putIfPresent(properties, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaConfig.sslTruststorePassword());
        putIfPresent(properties, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, kafkaConfig.sslTruststoreType());
        putIfPresent(properties, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaConfig.sslKeystoreLocation());
        putIfPresent(properties, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaConfig.sslKeystorePassword());
        putIfPresent(properties, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, kafkaConfig.sslKeystoreType());
        putIfPresent(properties, SslConfigs.SSL_KEY_PASSWORD_CONFIG, kafkaConfig.sslKeyPassword());
        putIfPresent(properties, SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
                kafkaConfig.sslEndpointIdentificationAlgorithm());
        return properties;
    }

    private void putIfPresent(Map<String, Object> target, String propertyName, java.util.Optional<String> value) {
        value.filter(candidate -> !candidate.isBlank())
                .ifPresent(candidate -> target.put(propertyName, candidate));
    }
}
