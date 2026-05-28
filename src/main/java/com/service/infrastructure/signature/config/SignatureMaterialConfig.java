package com.service.infrastructure.signature.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

/**
 * Typed signature material configuration rooted under {@code agw.connectivity.signature.ds}.
 *
 * <p>This mapping keeps the runtime compatible with a {@code keyId}-driven lookup model while
 * staying friendly to Quarkus config mapping validation. Material is grouped below
 * {@code keys.<keyId>} so dynamic partner or signer identifiers remain explicit children of a
 * known configuration root.</p>
 */
@ConfigMapping(prefix = "agw.connectivity.signature.ds")
public interface SignatureMaterialConfig {

    Map<String, KeyEntry> keys();

    Optional<TrustConfig> trust();

    interface KeyEntry {
        Optional<Pkcs12Config> pkcs12();

        Optional<CertificateConfig> certificate();
    }

    interface Pkcs12Config {
        Optional<String> path();

        @WithName("b64")
        Optional<String> base64();

        Optional<String> password();

        Optional<String> alias();
    }

    interface CertificateConfig {
        Optional<String> path();

        @WithName("b64")
        Optional<String> base64();
    }

    interface TrustConfig {
        Optional<CertificateConfig> ca();

        Optional<CrlConfig> crl();
    }

    interface CrlConfig {
        Optional<String> path();

        @WithName("b64")
        Optional<String> base64();
    }
}
