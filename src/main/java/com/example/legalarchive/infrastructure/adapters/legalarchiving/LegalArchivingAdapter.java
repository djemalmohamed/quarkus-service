package com.example.legalarchive.infrastructure.adapters.legalarchiving;

import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.application.port.out.LegalArchivingPort;
import com.example.legalarchive.infrastructure.adapters.legalarchiving.configuration.LegalArchivingProducerConfiguration;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Legal-archiving outbound adapter backed by Kafka.
 *
 * <p>When the feature is disabled, the underlying producer is never created,
 * which makes the archiving pipeline behave as if the transport adapter did not exist.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LegalArchivingAdapter implements LegalArchivingPort {

    private final LegalArchivingProducerConfiguration configuration;
    private final LegalArchivingEventProtoMapper protoMapper;
    private Producer<String, byte[]> producer;

    @PostConstruct
    void init() {
        if (!configuration.enabled()) {
            log.info("Legal archiving Kafka producer is disabled");
            return;
        }

        producer = createProducer(configuration.toProducerProperties());
        log.info("Legal archiving Kafka producer started for topic={}", configuration.topic());
    }

    @Override
    public Uni<Void> archive(LegalArchivingEvent event) {
        if (null == producer) {
            return Uni.createFrom().voidItem();
        }

        String requestId = null == event.requestId() ? "unknown-request" : event.requestId();
        String operation = null == event.operation() ? "unknown-operation" : event.operation();
        String phase = null == event.phase() ? "unknown-phase" : event.phase();
        String direction = null == event.direction() ? "unknown-direction" : event.direction();

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                configuration.topic(),
                requestId,
                protoMapper.toProto(event).toByteArray());

        return sendReactive(record)
                .invoke(metadata -> log.info(
                        "Legal archive emitted topic={} partition={} offset={} requestId={} operation={} phase={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        requestId,
                        operation,
                        phase))
                .onFailure().invoke(exception -> log.error(
                        "Legal archive emission failed requestId={} phase={} direction={}",
                        requestId,
                        phase,
                        direction,
                        exception))
                .replaceWithVoid();
    }

    /**
     * Adapts the native Kafka callback API to a Mutiny {@link Uni}.
     *
     * @param record the Kafka record to send asynchronously
     * @return a reactive wrapper around the resulting Kafka {@link RecordMetadata}
     */
    private Uni<RecordMetadata> sendReactive(ProducerRecord<String, byte[]> record) {
        return Uni.createFrom().emitter(emitter -> {
            try {
                producer.send(record, (metadata, exception) -> {
                    if (null != exception) {
                        emitter.fail(exception);
                        return;
                    }
                    emitter.complete(metadata);
                });
            } catch (RuntimeException exception) {
                emitter.fail(exception);
            }
        });
    }

    /**
     * Creates the native Kafka producer used by this adapter.
     *
     * @param properties the effective producer properties
     * @return the Kafka producer instance to use for subsequent sends
     */
    Producer<String, byte[]> createProducer(java.util.Properties properties) {
        return new KafkaProducer<>(properties);
    }

    /**
     * Flushes and closes the native Kafka producer when the CDI bean is destroyed.
     */
    @PreDestroy
    void close() {
        if (null != producer) {
            producer.flush();
            producer.close();
        }
    }
}
