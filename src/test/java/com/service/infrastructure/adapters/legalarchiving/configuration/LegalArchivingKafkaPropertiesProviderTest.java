package com.service.infrastructure.adapters.legalarchiving.configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalArchivingKafkaPropertiesProviderTest {

    @Test
    void shouldExtractOnlyLegalArchivingKafkaOverrides() {
        Config config = mock(Config.class);
        when(config.getPropertyNames()).thenReturn(List.of(
                "kafka.bootstrap.servers",
                "agw.connectivity.features.legal-archiving.kafka.bootstrap.servers",
                "agw.connectivity.features.legal-archiving.kafka.security.protocol",
                "agw.connectivity.features.legal-archiving.kafka.sasl.jaas.config",
                "agw.connectivity.features.legal-archiving.topic"));
        when(config.getOptionalValue(
                "agw.connectivity.features.legal-archiving.kafka.bootstrap.servers",
                String.class)).thenReturn(Optional.of("lea-broker:9094"));
        when(config.getOptionalValue(
                "agw.connectivity.features.legal-archiving.kafka.security.protocol",
                String.class)).thenReturn(Optional.of("SASL_SSL"));
        when(config.getOptionalValue(
                "agw.connectivity.features.legal-archiving.kafka.sasl.jaas.config",
                String.class)).thenReturn(Optional.of("jaas-config"));

        LegalArchivingKafkaPropertiesProvider provider = new LegalArchivingKafkaPropertiesProvider(config);

        Map<String, Object> properties = provider.overrideProperties();

        assertEquals(Map.of(
                "bootstrap.servers", "lea-broker:9094",
                "security.protocol", "SASL_SSL",
                "sasl.jaas.config", "jaas-config"), properties);
    }

    @Test
    void shouldIgnoreBlankLegalArchivingKafkaOverrides() {
        Config config = mock(Config.class);
        when(config.getPropertyNames()).thenReturn(List.of(
                "agw.connectivity.features.legal-archiving.kafka.bootstrap.servers",
                "agw.connectivity.features.legal-archiving.kafka.sasl.mechanism"));
        when(config.getOptionalValue(
                "agw.connectivity.features.legal-archiving.kafka.bootstrap.servers",
                String.class)).thenReturn(Optional.of(" "));
        when(config.getOptionalValue(
                "agw.connectivity.features.legal-archiving.kafka.sasl.mechanism",
                String.class)).thenReturn(Optional.empty());

        LegalArchivingKafkaPropertiesProvider provider = new LegalArchivingKafkaPropertiesProvider(config);

        assertEquals(Map.of(), provider.overrideProperties());
    }
}
