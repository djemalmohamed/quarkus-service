package com.service.infrastructure.signature.rfc.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Parsed representation of the inbound {@code Signature} header.
 */
public record ParsedSignature(
        String label,
        byte[] signatureBytes
) {

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParsedSignature that)) {
            return false;
        }
        return Objects.equals(label, that.label)
                && Arrays.equals(signatureBytes, that.signatureBytes);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(label) + Arrays.hashCode(signatureBytes);
    }

    @Override
    public String toString() {
        return "ParsedSignature{"
                + "label='" + label + '\''
                + ", signatureLength=" + (signatureBytes == null ? 0 : signatureBytes.length)
                + '}';
    }
}
