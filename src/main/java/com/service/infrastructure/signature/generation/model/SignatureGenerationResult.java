package com.service.infrastructure.signature.generation.model;

import com.service.infrastructure.signature.validation.model.SignatureData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result returned by the outbound signing service.
 *
 * <p>The result contains the generated transport headers, the resolved policy name and the
 * already prepared signature data that downstream concerns can reuse without recalculating RFC
 * component values.</p>
 */
public record SignatureGenerationResult(
        String policy,
        Map<String, String> headers,
        SignatureData signatureData
) {
    /**
     * Creates a result without prepared signature data.
     *
     * @param policy resolved policy name
     * @param headers generated transport headers
     */
    public SignatureGenerationResult(String policy, Map<String, String> headers) {
        this(policy, headers, null);
    }

    /**
     * Defensively copies mutable generated headers to keep the result immutable.
     */
    public SignatureGenerationResult {
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        signatureData = signatureData == null ? null : signatureData.copy();
    }
}
