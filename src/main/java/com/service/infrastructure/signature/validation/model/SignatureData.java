package com.service.infrastructure.signature.validation.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signature data prepared by the validation process for downstream archiving needs.
 *
 * <p>The validation layer is the only place that knows how to resolve the effective value of each
 * covered component according to the HTTP Message Signatures rules. This record exposes that
 * already-resolved material so downstream concerns such as legal archiving can reuse it without
 * recalculating or duplicating RFC-specific logic.</p>
 */
public record SignatureData(
        byte[] signature,
        String signatureInput,
        Map<String, String> componentValues
) {

    /**
     * Creates an immutable view of the validated signature material.
     *
     * @param signature full {@code Signature} header value for the selected label, encoded as bytes
     * @param signatureInput full {@code Signature-Input} header value for the selected label
     * @param componentValues covered component identifiers associated with their resolved values
     */
    public SignatureData {
        signature = null == signature ? null : signature.clone();
        componentValues = null == componentValues
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(componentValues));
    }

    /**
     * Returns a defensive copy of the signature bytes.
     */
    @Override
    public byte[] signature() {
        return null == signature ? null : signature.clone();
    }

    /**
     * Creates a safe copy of this record for embedding into other immutable report models.
     *
     * @return copied signature data
     */
    public SignatureData copy() {
        return new SignatureData(signature, signatureInput, componentValues);
    }

    @Override
    public String toString() {
        return "SignatureData{"
                + "signatureLength=" + (null == signature ? 0 : signature.length)
                + ", signatureInput='" + signatureInput + "'"
                + ", componentNames=" + componentValues.keySet()
                + '}';
    }
}
