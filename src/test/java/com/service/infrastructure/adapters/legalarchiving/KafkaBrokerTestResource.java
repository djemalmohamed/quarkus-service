package com.service.infrastructure.adapters.legalarchiving;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Starts a disposable Kafka broker for integration tests and publishes its bootstrap address to Quarkus.
 */
public class KafkaBrokerTestResource implements QuarkusTestResourceLifecycleManager {

    private KafkaContainer kafkaContainer;

    @Override
    public Map<String, String> start() {
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
        kafkaContainer.start();
        return Map.of(
                "agw.connectivity.features.legal-archiving.topic", "desp-agc-legal-archive-it",
                "kafka.client.id", "quarkus-service-it",
                "kafka.bootstrap.servers", kafkaContainer.getBootstrapServers()
        );
    }

    @Override
    public void stop() {
        if (null != kafkaContainer) {
            kafkaContainer.stop();
        }
    }
}
