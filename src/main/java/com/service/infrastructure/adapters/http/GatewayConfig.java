package com.service.infrastructure.adapters.http;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound HTTP configuration used by the gateway HTTP adapters.
 *
 * <p>The configuration is colocated with the HTTP adapter package because it only serves
 * outbound transport concerns: shared client timeouts and named downstream targets.</p>
 */
@ConfigMapping(prefix = "gateway")
public interface GatewayConfig {

    /**
     * Shared outbound HTTP client settings.
     *
     * @return outbound HTTP settings
     */
    OutboundConfig outbound();

    /**
     * Configured downstream targets addressable by logical name.
     *
     * @return targets keyed by business-facing target name
     */
    Map<String, TargetConfig> targets();

    /**
     * Shared outbound HTTP timeout settings.
     */
    interface OutboundConfig {
        /**
         * Maximum time allowed to establish the HTTP connection.
         *
         * @return connect timeout
         */
        @WithDefault("10S")
        Duration connectTimeout();

        /**
         * Maximum time allowed for the downstream exchange.
         *
         * @return read timeout
         */
        @WithDefault("60S")
        Duration readTimeout();
    }

    /**
     * Definition of one downstream target used by an output adapter.
     */
    interface TargetConfig {
        /**
         * Optional human-readable description of the downstream target.
         *
         * @return target description when configured
         */
        Optional<String> description();

        /**
         * Base URL used when callers address the target by logical name only.
         *
         * @return downstream base URL when configured
         */
        Optional<String> baseUrl();

        /**
         * Default HTTP method used when callers do not override it explicitly.
         *
         * @return default outbound HTTP method
         */
        @WithDefault("GET")
        String method();

        /**
         * Default headers automatically injected for this target.
         *
         * @return configured default HTTP headers, or an empty map when none are defined
         */
        Map<String, String> headers();

        /**
         * Optional outbound signature endpoint name used to sign requests sent to this target.
         *
         * @return generation endpoint name when configured
         */
        Optional<String> requestSignatureEndpoint();

        /**
         * Optional validation endpoint name used to validate synchronous responses from this target.
         *
         * @return validation endpoint name when configured
         */
        Optional<String> responseSignatureEndpoint();
    }
}
