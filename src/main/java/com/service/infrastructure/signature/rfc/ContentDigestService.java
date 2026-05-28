package com.service.infrastructure.signature.rfc;

import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

/**
 * Shared digest helper used by both outbound generation and inbound validation.
 */
@ApplicationScoped
public class ContentDigestService {

    public byte[] computeDigest(byte[] body, String digestName) {
        String normalizedDigestName = SignatureConstants.normalize(digestName);
        String javaDigest = resolveJavaDigestName(normalizedDigestName);
        if (javaDigest == null) {
            throw new SignatureConfigurationException("Unsupported digest algorithm: " + digestName);
        }

        try {
            return MessageDigest.getInstance(javaDigest).digest(body == null ? new byte[0] : body);
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to compute digest", error);
        }
    }

    public String buildDigestHeader(byte[] body, String digestName) {
        String normalizedDigestName = digestName.toLowerCase(Locale.ROOT);
        byte[] digest = computeDigest(body, normalizedDigestName);
        return normalizedDigestName + "=:" + Base64.getEncoder().encodeToString(digest) + ":";
    }

    public String resolveJavaDigestName(String digestName) {
        return switch (SignatureConstants.normalize(digestName)) {
            case SignatureConstants.DIGEST_SHA_256 -> SignatureConstants.JCA_DIGEST_SHA_256;
            case SignatureConstants.DIGEST_SHA_512 -> SignatureConstants.JCA_DIGEST_SHA_512;
            default -> null;
        };
    }
}
