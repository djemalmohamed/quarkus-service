package com.service.infrastructure.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeKafkaProducerPropertiesFactoryTest {

    @Test
    void shouldReportInfrastructureEnabledOnlyWhenKafkaAndProducersAreEnabled() {
        KafkaConfig kafkaConfig = mock(KafkaConfig.class);
        when(kafkaConfig.enabled()).thenReturn(true);
        when(kafkaConfig.producersEnabled()).thenReturn(true);

        NativeKafkaProducerPropertiesFactory factory = new NativeKafkaProducerPropertiesFactory(kafkaConfig, Map.of());
        assertTrue(factory.infrastructureEnabled());

        when(kafkaConfig.producersEnabled()).thenReturn(false);
        assertFalse(factory.infrastructureEnabled());
    }

    @Test
    void shouldCopyOnlyProducerPropertiesAndAppendClientIdSuffix() {
        KafkaConfig kafkaConfig = mock(KafkaConfig.class);
        Map<String, Object> defaultKafkaBroker = new HashMap<>();
        defaultKafkaBroker.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        defaultKafkaBroker.put(ProducerConfig.RETRIES_CONFIG, "7");
        defaultKafkaBroker.put(ProducerConfig.CLIENT_ID_CONFIG, "gateway");
        defaultKafkaBroker.put("custom.unrelated.property", "ignored");

        NativeKafkaProducerPropertiesFactory factory = new NativeKafkaProducerPropertiesFactory(kafkaConfig, defaultKafkaBroker);

        Properties properties = factory.createProducerProperties("legal-archiving");

        assertEquals("kafka:9092", properties.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("7", properties.getProperty(ProducerConfig.RETRIES_CONFIG));
        assertEquals("gateway-legal-archiving", properties.getProperty(ProducerConfig.CLIENT_ID_CONFIG));
        assertNull(properties.get("custom.unrelated.property"));
    }

    @Test
    void shouldFallbackToDefaultClientIdWhenMissingOrBlankSuffix() {
        KafkaConfig kafkaConfig = mock(KafkaConfig.class);
        NativeKafkaProducerPropertiesFactory factory = new NativeKafkaProducerPropertiesFactory(kafkaConfig, Map.of());

        Properties properties = factory.createProducerProperties("   ");

        assertEquals("agw-connectivity", properties.getProperty(ProducerConfig.CLIENT_ID_CONFIG));
    }
}
