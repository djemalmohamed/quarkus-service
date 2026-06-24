package com.service.infrastructure.adapters.legalarchiving.configuration;

import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalArchivingKafkaPropertiesProviderTest {

    @Test
    void shouldExtractOnlyLegalArchivingKafkaOverrides() {
        LegalArchivingKafkaConfig config = mock(LegalArchivingKafkaConfig.class);
        when(config.bootstrapServers()).thenReturn(Optional.of("lea-broker:9094"));
        when(config.securityProtocol()).thenReturn(Optional.of("SASL_SSL"));
        when(config.saslJaasConfig()).thenReturn(Optional.of("jaas-config"));
        when(config.saslMechanism()).thenReturn(Optional.of("PLAIN"));
        when(config.saslClientCallbackHandlerClass()).thenReturn(Optional.empty());
        when(config.saslLoginCallbackHandlerClass()).thenReturn(Optional.empty());
        when(config.saslLoginClass()).thenReturn(Optional.empty());
        when(config.sslTruststoreLocation()).thenReturn(Optional.of("/tmp/truststore.p12"));
        when(config.sslTruststorePassword()).thenReturn(Optional.of("changeit"));
        when(config.sslTruststoreType()).thenReturn(Optional.of("PKCS12"));
        when(config.sslKeystoreLocation()).thenReturn(Optional.empty());
        when(config.sslKeystorePassword()).thenReturn(Optional.empty());
        when(config.sslKeystoreType()).thenReturn(Optional.empty());
        when(config.sslKeyPassword()).thenReturn(Optional.empty());
        when(config.sslEndpointIdentificationAlgorithm()).thenReturn(Optional.of("HTTPS"));

        LegalArchivingKafkaPropertiesProvider provider = new LegalArchivingKafkaPropertiesProvider(config);

        Map<String, Object> properties = provider.overrideProperties();

        assertEquals(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "lea-broker:9094",
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL",
                SaslConfigs.SASL_JAAS_CONFIG, "jaas-config",
                SaslConfigs.SASL_MECHANISM, "PLAIN",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "/tmp/truststore.p12",
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "changeit",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PKCS12",
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS"), properties);
    }

    @Test
    void shouldIgnoreBlankLegalArchivingKafkaOverrides() {
        LegalArchivingKafkaConfig config = mock(LegalArchivingKafkaConfig.class);
        when(config.bootstrapServers()).thenReturn(Optional.of(" "));
        when(config.securityProtocol()).thenReturn(Optional.empty());
        when(config.saslMechanism()).thenReturn(Optional.empty());
        when(config.saslJaasConfig()).thenReturn(Optional.empty());
        when(config.saslClientCallbackHandlerClass()).thenReturn(Optional.empty());
        when(config.saslLoginCallbackHandlerClass()).thenReturn(Optional.empty());
        when(config.saslLoginClass()).thenReturn(Optional.empty());
        when(config.sslTruststoreLocation()).thenReturn(Optional.empty());
        when(config.sslTruststorePassword()).thenReturn(Optional.empty());
        when(config.sslTruststoreType()).thenReturn(Optional.empty());
        when(config.sslKeystoreLocation()).thenReturn(Optional.empty());
        when(config.sslKeystorePassword()).thenReturn(Optional.empty());
        when(config.sslKeystoreType()).thenReturn(Optional.empty());
        when(config.sslKeyPassword()).thenReturn(Optional.empty());
        when(config.sslEndpointIdentificationAlgorithm()).thenReturn(Optional.empty());

        LegalArchivingKafkaPropertiesProvider provider = new LegalArchivingKafkaPropertiesProvider(config);

        assertEquals(Map.of(), provider.overrideProperties());
    }

    @Test
    void shouldRejectPartialOverridesWhenBootstrapServersAreMissing() {
        LegalArchivingKafkaConfig config = mock(LegalArchivingKafkaConfig.class);
        when(config.bootstrapServers()).thenReturn(Optional.empty());
        when(config.securityProtocol()).thenReturn(Optional.of("SASL_SSL"));
        when(config.saslMechanism()).thenReturn(Optional.empty());
        when(config.saslJaasConfig()).thenReturn(Optional.empty());
        when(config.saslClientCallbackHandlerClass()).thenReturn(Optional.empty());
        when(config.saslLoginCallbackHandlerClass()).thenReturn(Optional.empty());
        when(config.saslLoginClass()).thenReturn(Optional.empty());
        when(config.sslTruststoreLocation()).thenReturn(Optional.empty());
        when(config.sslTruststorePassword()).thenReturn(Optional.empty());
        when(config.sslTruststoreType()).thenReturn(Optional.empty());
        when(config.sslKeystoreLocation()).thenReturn(Optional.empty());
        when(config.sslKeystorePassword()).thenReturn(Optional.empty());
        when(config.sslKeystoreType()).thenReturn(Optional.empty());
        when(config.sslKeyPassword()).thenReturn(Optional.empty());
        when(config.sslEndpointIdentificationAlgorithm()).thenReturn(Optional.empty());

        LegalArchivingKafkaPropertiesProvider provider = new LegalArchivingKafkaPropertiesProvider(config);

        assertThrows(IllegalStateException.class, provider::overrideProperties);
    }
}
