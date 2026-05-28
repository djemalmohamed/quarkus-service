package com.playground.gateway.infrastructure.signature.validation.model;

import com.playground.gateway.infrastructure.signature.rfc.model.CoveredComponent;

import java.time.Duration;
import java.util.List;

/**
 * Runtime-ready validation rule obtained after configuration resolution.
 *
 * <p>This model is the compact form consumed by the validation engine. Human-readable matcher
 * expressions and field declarations have already been translated into concrete checks.</p>
 */
public record ResolvedValidationPolicy(
        String name,
        boolean failFast,
        boolean bodyRequired,
        List<String> requiredHeaders,
        List<CoveredComponent> requiredComponents,
        String signatureAlgorithm,
        String digestAlgorithm,
        boolean requireDigest,
        boolean requireCreated,
        boolean requireExpires,
        Duration maxSignatureAge,
        Duration clockSkew,
        boolean requireNonce,
        String nonceCheckMode,
        boolean rejectOnFailure
) {
}
