package com.service.infrastructure.signature.shared.model;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * Runtime representation of the asymmetric key material required by signing or verification.
 */
public record ResolvedKeyMaterial(
        String keyId,
        String type,
        PublicKey publicKey,
        PrivateKey privateKey,
        X509Certificate certificate
) {
}
