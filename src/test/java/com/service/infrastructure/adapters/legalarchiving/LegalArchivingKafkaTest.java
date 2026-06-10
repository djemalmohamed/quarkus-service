package com.service.infrastructure.adapters.legalarchiving;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.port.in.LegalArchivingInPort;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Tag("kafka-integration")
@QuarkusTestResource(value = KafkaBrokerTestResource.class, restrictToAnnotatedClass = true)
class LegalArchivingKafkaTest {

    @Inject
    LegalArchivingInPort legalArchivingInPort;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "agw.connectivity.features.legal-archiving.topic")
    String topic;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPublishLegalArchivingEventToKafkaTopic() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(500));

            LegalArchivingEvent event = new LegalArchivingEvent(
                    "event-it-1",
                    "POST /v1/payments",
                    "INBOUND",
                    "REQUEST",
                    "POST",
                    "/v1/payments",
                    "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8),
                    "sig1=:AQID:",
                    "sig1=(\"@method\" \"request-id\")",
                    List.of(
                            new LegalArchivingEvent.SignatureComponent("@method", "POST"),
                            new LegalArchivingEvent.SignatureComponent("request-id", "event-it-1")
                    )
            );

            legalArchivingInPort.archive(event).await().atMost(Duration.ofSeconds(10));

            ConsumerRecord<String, byte[]> record = pollOneRecord(consumer, Duration.ofSeconds(10));
            assertEquals("event-it-1", record.key());

            JsonNode root = objectMapper.readTree(record.value());
            assertNotNull(root.get("lea_signature_data"));
            assertNotNull(root.get("signature_params"));
            assertNotNull(root.get("legal_core_data"));
            assertTrue(root.get("lea_additional_data").isObject());
            assertEquals(
                    Base64.getEncoder().encodeToString(event.payload()),
                    root.at("/legal_core_data/payload").asText()
            );
            assertEquals("sig1=:AQID:", root.at("/lea_signature_data/signature").asText());
            assertEquals(
                    "sig1=(\"@method\" \"request-id\")",
                    root.at("/lea_signature_data/signature_input").asText()
            );
            assertEquals("POST", root.at("/lea_additional_data/http_method").asText());
            assertEquals("/v1/payments", root.at("/lea_additional_data/http_path").asText());
            assertEquals("@method", root.at("/signature_params/signature_parameter/0/signature_param_key").asText());
            assertEquals("POST", root.at("/signature_params/signature_parameter/0/signature_param_value").asText());
        }
    }

    private KafkaConsumer<String, byte[]> createConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "legal-archiving-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, byte[]> pollOneRecord(KafkaConsumer<String, byte[]> consumer, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("No Kafka record received on topic " + topic + " within " + timeout);
    }
}
