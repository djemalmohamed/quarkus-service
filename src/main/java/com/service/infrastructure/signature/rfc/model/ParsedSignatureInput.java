package com.service.infrastructure.signature.rfc.model;

import java.util.List;

/**
 * Structured representation of the inbound {@code Signature-Input} header.
 *
 * <p>This record preserves the label, covered components and optional metadata parameters exactly
 * as they were parsed so downstream validation and canonicalization can rebuild the same logical
 * signature context.</p>
 */
public record ParsedSignatureInput(
        String label,
        List<CoveredComponent> components,
        String signatureParams,
        String algorithm,
        String keyId,
        Long created,
        Long expires,
        String nonce
) {
}
