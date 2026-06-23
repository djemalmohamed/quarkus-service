package com.service.infrastructure.adapters.legalarchiving.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.Optional;

/**
 * Dedicated Kafka connection overrides for legal archiving.
 *
 * <p>These properties are optional. When none are configured, legal archiving inherits the shared
 * AGR Kafka connection. When some are configured, they selectively override the shared connection
 * only for the legal-archiving producer.</p>
 */
@ConfigMapping(prefix = "agw.connectivity.features.legal-archiving.kafka")
public interface LegalArchivingKafkaConfig {

    /**
     * @return dedicated Kafka bootstrap servers for legal archiving
     */
    @WithName("bootstrap.servers")
    Optional<String> bootstrapServers();

    /**
     * @return dedicated security protocol for legal archiving
     */
    @WithName("security.protocol")
    Optional<String> securityProtocol();

    /**
     * @return dedicated SASL mechanism for legal archiving
     */
    @WithName("sasl.mechanism")
    Optional<String> saslMechanism();

    /**
     * @return dedicated SASL JAAS configuration for legal archiving
     */
    @WithName("sasl.jaas.config")
    Optional<String> saslJaasConfig();

    /**
     * @return dedicated SASL client callback handler class
     */
    @WithName("sasl.client.callback.handler.class")
    Optional<String> saslClientCallbackHandlerClass();

    /**
     * @return dedicated SASL login callback handler class
     */
    @WithName("sasl.login.callback.handler.class")
    Optional<String> saslLoginCallbackHandlerClass();

    /**
     * @return dedicated SASL login implementation class
     */
    @WithName("sasl.login.class")
    Optional<String> saslLoginClass();

    /**
     * @return dedicated SSL truststore location
     */
    @WithName("ssl.truststore.location")
    Optional<String> sslTruststoreLocation();

    /**
     * @return dedicated SSL truststore password
     */
    @WithName("ssl.truststore.password")
    Optional<String> sslTruststorePassword();

    /**
     * @return dedicated SSL truststore type
     */
    @WithName("ssl.truststore.type")
    Optional<String> sslTruststoreType();

    /**
     * @return dedicated SSL keystore location
     */
    @WithName("ssl.keystore.location")
    Optional<String> sslKeystoreLocation();

    /**
     * @return dedicated SSL keystore password
     */
    @WithName("ssl.keystore.password")
    Optional<String> sslKeystorePassword();

    /**
     * @return dedicated SSL keystore type
     */
    @WithName("ssl.keystore.type")
    Optional<String> sslKeystoreType();

    /**
     * @return dedicated SSL key password
     */
    @WithName("ssl.key.password")
    Optional<String> sslKeyPassword();

    /**
     * @return dedicated SSL endpoint identification algorithm
     */
    @WithName("ssl.endpoint.identification.algorithm")
    Optional<String> sslEndpointIdentificationAlgorithm();
}
