package com.playground.gateway.infrastructure.signature.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Root signature configuration for the gateway.
 */
@ConfigMapping(prefix = "agw.connectivity.signature")
public interface SignatureConfig {

    @WithDefault("false")
    boolean enabled();

    @WithDefault("false")
    boolean logValidationDetails();

    DirectionConfig inbound();

    RevocationConfig revocation();

    DirectionConfig outbound();

    interface DirectionConfig {
        ValidationConfig validation();

        GenerationConfig generation();
    }

    interface RevocationConfig {
        @WithDefault("false")
        boolean enabled();
    }

    interface ValidationConfig {
        Map<String, ValidationEndpointConfig> endpoints();
    }

    interface ValidationEndpointConfig {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("ecdsa-p256-sha256")
        String algorithm();

        @WithDefault("sha-256")
        String digestAlgorithm();

        @WithDefault("false")
        boolean failFast();

        @WithDefault("header:method,header:path,header:content-digest")
        List<String> fields();

        @WithDefault("true")
        boolean requireCreated();

        @WithDefault("false")
        boolean requireExpires();

        @WithDefault("false")
        boolean requireNonce();

        @WithDefault("NONE")
        String nonceCheckMode();

        @WithDefault("PT5M")
        Duration maxSignatureAge();

        @WithDefault("PT1M")
        Duration clockSkew();

        @WithDefault("true")
        boolean rejectOnFailure();

        @WithDefault("ALL")
        String matcher();
    }

    interface GenerationConfig {
        Map<String, GenerationEndpointConfig> endpoints();
    }

    interface GenerationEndpointConfig {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("ALL")
        String matcher();

        Optional<String> targetName();

        @WithDefault("sig")
        String signatureLabel();

        String keyId();

        String algorithm();

        @WithDefault("sha-256")
        String digestAlgorithm();

        @WithDefault("header:method,header:path,header:content-digest")
        List<String> fields();

        @WithDefault("true")
        boolean includeCreated();

        @WithDefault("false")
        boolean includeExpires();

        @WithDefault("PT5M")
        Duration expiresIn();

        @WithDefault("false")
        boolean includeNonce();

        @WithDefault("24")
        int nonceLength();
    }
}
