package com.service.infrastructure.adapters.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.infrastructure.adapters.legalarchiving.configuration.LegalArchivingProducerConfiguration;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalArchivingAdapterTest {

    @Mock
    private LegalArchivingProducerConfiguration configuration;

    @Mock
    private Producer<String, byte[]> producer;

    private final LegalArchivingEventProtoMapper protoMapper = new LegalArchivingEventProtoMapper();

    @Test
    void shouldNotCreateProducerWhenFeatureIsDisabled() {
        when(configuration.enabled()).thenReturn(false);
        TestableLegalArchivingAdapter adapter = new TestableLegalArchivingAdapter(configuration, protoMapper, producer);

        adapter.init();
        assertDoesNotThrow(() -> adapter.archive(sampleEvent("event-1")).await().indefinitely());

        verifyNoInteractions(producer);
    }

    @Test
    void shouldSendLegalArchivingEventToKafka() {
        when(configuration.enabled()).thenReturn(true);
        when(configuration.topic()).thenReturn("archive-topic");
        when(configuration.toProducerProperties()).thenReturn(new Properties());
        doAnswer(invocation -> {
            ProducerRecord<String, byte[]> record = invocation.getArgument(0);
            Callback callback = invocation.getArgument(1);
            callback.onCompletion(
                    new RecordMetadata(new TopicPartition(record.topic(), 2), 15L, 0, 0L, 0, 0),
                    null
            );
            return null;
        }).when(producer).send(any(), any());

        TestableLegalArchivingAdapter adapter = new TestableLegalArchivingAdapter(configuration, protoMapper, producer);
        adapter.init();
        adapter.archive(sampleEvent("event-1")).await().indefinitely();

        ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(recordCaptor.capture(), any());
        ProducerRecord<String, byte[]> record = recordCaptor.getValue();
        assertEquals("archive-topic", record.topic());
        assertEquals("event-1", record.key());
    }

    @Test
    void shouldPropagateSendFailureAndUseFallbackLogValues() {
        when(configuration.enabled()).thenReturn(true);
        when(configuration.topic()).thenReturn("archive-topic");
        when(configuration.toProducerProperties()).thenReturn(new Properties());
        doThrow(new IllegalStateException("boom")).when(producer).send(any(), any());

        TestableLegalArchivingAdapter adapter = new TestableLegalArchivingAdapter(configuration, protoMapper, producer);
        adapter.init();

        assertThrows(IllegalStateException.class, () -> adapter.archive(new LegalArchivingEvent(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        )).await().indefinitely());
    }

    @Test
    void shouldPropagateCallbackFailure() {
        when(configuration.enabled()).thenReturn(true);
        when(configuration.topic()).thenReturn("archive-topic");
        when(configuration.toProducerProperties()).thenReturn(new Properties());
        doAnswer(invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.onCompletion(null, new IllegalStateException("callback-boom"));
            return null;
        }).when(producer).send(any(), any());

        TestableLegalArchivingAdapter adapter = new TestableLegalArchivingAdapter(configuration, protoMapper, producer);
        adapter.init();

        assertThrows(IllegalStateException.class, () -> adapter.archive(sampleEvent("event-1")).await().indefinitely());
    }

    @Test
    void shouldFlushAndCloseProducerOnShutdown() {
        when(configuration.enabled()).thenReturn(true);
        when(configuration.topic()).thenReturn("archive-topic");
        when(configuration.toProducerProperties()).thenReturn(new Properties());

        TestableLegalArchivingAdapter adapter = new TestableLegalArchivingAdapter(configuration, protoMapper, producer);
        adapter.init();
        adapter.close();

        verify(producer).flush();
        verify(producer).close();
    }

    @Test
    void shouldIgnoreCloseWhenProducerWasNeverCreated() {
        when(configuration.enabled()).thenReturn(false);

        TestableLegalArchivingAdapter adapter = new TestableLegalArchivingAdapter(configuration, protoMapper, producer);
        adapter.init();
        adapter.close();

        verifyNoInteractions(producer);
    }

    @Test
    void shouldCreateNativeKafkaProducerFromProperties() {
        LegalArchivingAdapter adapter = new LegalArchivingAdapter(configuration, protoMapper);
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");

        Producer<String, byte[]> createdProducer = adapter.createProducer(properties);

        assertInstanceOf(KafkaProducer.class, createdProducer);
        createdProducer.close();
    }

    private LegalArchivingEvent sampleEvent(String eventId) {
        return new LegalArchivingEvent(
                eventId,
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "{\"uetr\":\"abc\"}".getBytes(StandardCharsets.UTF_8),
                "sig1=:AQID:".getBytes(StandardCharsets.UTF_8),
                "sig1=(\"@method\")",
                List.of(new LegalArchivingEvent.SignatureComponent("@method", "POST"))
        );
    }

    private static final class TestableLegalArchivingAdapter extends LegalArchivingAdapter {

        private final Producer<String, byte[]> testProducer;

        private TestableLegalArchivingAdapter(
                LegalArchivingProducerConfiguration configuration,
                LegalArchivingEventProtoMapper protoMapper,
                Producer<String, byte[]> testProducer
        ) {
            super(configuration, protoMapper);
            this.testProducer = testProducer;
        }

        @Override
        Producer<String, byte[]> createProducer(Properties properties) {
            return testProducer;
        }
    }
}
