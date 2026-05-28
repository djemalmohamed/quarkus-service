package com.service.infrastructure.signature.rfc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of one HTTP Message Signatures canonicalization pass.
 *
 * <p>The RFC-facing source of truth remains the UTF-8 signature base bytes. The component map is
 * an auxiliary view produced from the same resolution loop so downstream services can reuse the
 * effective covered-component values without reparsing the serialized signature base.</p>
 *
 * <p>This keeps canonicalization standards-compliant while avoiding a second pass over the
 * serialized bytes in validation, generation, or legal-archiving integration code. The signature
 * base remains the authoritative RFC artifact; {@code componentValues} is only a convenience view
 * derived from exactly the same covered-component resolution step.</p>
 *
 * @param signatureBase UTF-8 encoded signature base
 * @param componentValues resolved component values keyed by normalized component identifier
 */
public record CanonicalizationResult(
        byte[] signatureBase,
        Map<String, String> componentValues
) {

    /**
     * Creates an immutable canonicalization result safe to reuse across layers.
     */
    public CanonicalizationResult {
        signatureBase = null == signatureBase ? new byte[0] : signatureBase.clone();
        componentValues = null == componentValues
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(componentValues));
    }

    /**
     * Returns one defensive copy of the canonical UTF-8 signature base.
     *
     * <p>The returned bytes are the exact bytes to be signed or verified according to the HTTP
     * Message Signatures RFC processing rules.</p>
     *
     * @return defensive copy of the canonical signature base
     */
    @Override
    public byte[] signatureBase() {
        return signatureBase.clone();
    }

    /**
     * Returns the covered-component values resolved during the same canonicalization pass.
     *
     * <p>The map preserves component order so downstream consumers such as legal archiving can
     * emit the same key/value order that was used while constructing the signature base.</p>
     *
     * @return immutable view of resolved component values
     */
    @Override
    public Map<String, String> componentValues() {
        return componentValues;
    }
}
