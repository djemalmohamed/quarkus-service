package com.service.infrastructure.signature.validation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Signature data prepared by the validation process for downstream archiving needs.
 *
 * <p>The validation layer is the only place that knows how to resolve the effective value of each
 * covered component according to the HTTP Message Signatures rules. This record exposes that
 * already-resolved material so downstream concerns such as legal archiving can reuse it without
 * recalculating or duplicating RFC-specific logic.</p>
 */
public record SignatureData(
        String signature,
        String signatureInput,
        Map<String, String> componentValues
) {

    /**
     * Creates an immutable view of the validated signature material.
     *
     * @param signature full {@code Signature} header value for the selected label
     * @param signatureInput full {@code Signature-Input} header value for the selected label
     * @param componentValues covered component identifiers associated with their resolved values
     */
    public SignatureData {
        componentValues = null == componentValues
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(componentValues));
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SignatureData that)) {
            return false;
        }
        return Objects.equals(signature, that.signature)
                && Objects.equals(signatureInput, that.signatureInput)
                && Objects.equals(componentValues, that.componentValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature, signatureInput, componentValues);
    }

    @Override
    public String toString() {
        return "SignatureData{"
                + "signature='" + signature + '\''
                + ", signatureInput='" + signatureInput + "'"
                + ", componentNames=" + componentValues.keySet()
                + '}';
    }
}
