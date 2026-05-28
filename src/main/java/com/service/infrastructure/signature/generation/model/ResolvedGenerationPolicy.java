package com.service.infrastructure.signature.generation.model;

import com.service.infrastructure.signature.rfc.model.CoveredComponent;

import java.time.Duration;
import java.util.List;

/**
 * Runtime-ready generation rule obtained after configuration resolution.
 *
 * <p>This model is the compact form consumed by the signing service. Human-readable configured
 * fields have already been expanded into the exact component list that must appear in
 * {@code Signature-Input} and in the canonical signature base.</p>
 */
public record ResolvedGenerationPolicy(
        String name,
        String signatureLabel,
        String keyId,
        String signatureAlgorithm,
        String digestAlgorithm,
        List<CoveredComponent> components,
        boolean includeDigest,
        boolean includeCreated,
        boolean includeExpires,
        Duration expiresIn,
        boolean includeNonce,
        int nonceLength
) {
}
