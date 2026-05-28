package com.example.legalarchive.infrastructure.signature.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signature information already prepared by the signature process for downstream consumers.
 *
 * @param signature the decoded signature bytes ready to be mapped to downstream contracts
 * @param signatureInput the full {@code Signature-Input} header value, including its label
 * @param componentValues the covered component identifiers associated with their resolved values
 */
public record SignatureData(
        byte[] signature,
        String signatureInput,
        Map<String, String> componentValues) {

    /**
     * Creates immutable signature data.
     */
    public SignatureData {
        signature = null == signature ? null : signature.clone();
        componentValues = null == componentValues
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(componentValues));
    }

    @Override
    public byte[] signature() {
        return null == signature ? null : signature.clone();
    }
}
