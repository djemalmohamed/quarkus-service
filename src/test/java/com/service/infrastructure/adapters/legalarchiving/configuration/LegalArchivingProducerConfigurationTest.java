package com.service.infrastructure.adapters.legalarchiving.configuration;

import com.service.infrastructure.configuration.NativeKafkaProducerPropertiesFactory;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalArchivingProducerConfigurationTest {

    private NativeKafkaProducerPropertiesFactory propertiesFactory;
    private LegalArchivingFeatureConfig featureConfig;
    private LegalArchivingFeatureConfig.Producer producerConfig;
    private LegalArchivingProducerConfiguration configuration;

    @BeforeEach
    void setUp() {
        propertiesFactory = mock(NativeKafkaProducerPropertiesFactory.class);
        featureConfig = mock(LegalArchivingFeatureConfig.class);
        producerConfig = mock(LegalArchivingFeatureConfig.Producer.class);
        configuration = new LegalArchivingProducerConfiguration(propertiesFactory, featureConfig);

        when(featureConfig.producer()).thenReturn(producerConfig);
    }

    @Test
    void shouldBeEnabledOnlyWhenInfrastructureAndFeatureAreEnabled() {
        when(propertiesFactory.infrastructureEnabled()).thenReturn(true);
        when(featureConfig.enabled()).thenReturn(true);
        assertTrue(configuration.enabled());

        when(featureConfig.enabled()).thenReturn(false);
        assertFalse(configuration.enabled());
    }

    @Test
    void shouldBuildProducerPropertiesWithLegalArchivingOverrides() {
        Properties baseProperties = new Properties();
        baseProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        when(propertiesFactory.createProducerProperties("archive")).thenReturn(baseProperties);
        when(featureConfig.producerClientIdSuffix()).thenReturn("archive");
        when(featureConfig.topic()).thenReturn("legal-topic");
        when(producerConfig.acks()).thenReturn("all");
        when(producerConfig.retries()).thenReturn(3);
        when(producerConfig.requestTimeoutMs()).thenReturn(15000);
        when(producerConfig.enableIdempotence()).thenReturn(true);

        Properties properties = configuration.toProducerProperties();

        assertEquals("legal-topic", configuration.topic());
        assertEquals("all", properties.getProperty(ProducerConfig.ACKS_CONFIG));
        assertEquals("3", properties.getProperty(ProducerConfig.RETRIES_CONFIG));
        assertEquals("15000", properties.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG));
        assertEquals("true", properties.getProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(StringSerializer.class.getName(), properties.getProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(ByteArraySerializer.class.getName(), properties.getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
    }
}
