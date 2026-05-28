package com.playground.gateway.infrastructure.signature.validation.service;

import com.playground.gateway.infrastructure.signature.rfc.ContentDigestService;
import com.playground.gateway.infrastructure.signature.rfc.StructuredFieldSupport;
import com.playground.gateway.infrastructure.signature.shared.SignatureConstants;
import com.playground.gateway.infrastructure.signature.shared.service.HeaderUtils;
import com.playground.gateway.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.playground.gateway.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.playground.gateway.infrastructure.signature.validation.model.SignatureValidationCode;
import com.playground.gateway.infrastructure.signature.validation.model.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Validates the inbound {@code Content-Digest} transport header.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DigestValidationService {

    private final ContentDigestService contentDigestService;

    /**
     * Validates the digest header carried by an inbound message against the actual payload bytes.
     *
     * @param message inbound HTTP message
     * @param policy resolved validation policy
     * @param keyId resolved signature key identifier when available
     * @param algorithm normalized signature algorithm when available
     * @return a validation failure result, or {@code null} when the digest is valid or not required
     */
    public ValidationResult validate(IncomingHttpMessage message, ResolvedValidationPolicy policy, String keyId, String algorithm) {
        var digestHeaderValues = HeaderUtils.matchingHeaderValues(message.headers(), SignatureConstants.HEADER_CONTENT_DIGEST);
        if (digestHeaderValues.isEmpty()) {
            if (policy.requireDigest()) {
                return ValidationResult.invalid(SignatureValidationCode.SIG_MISSING_DIGEST, "Missing Content-Digest header", policy.name(), keyId, algorithm, Map.of());
            }
            return null;
        }

        if (digestHeaderValues.size() > 1) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Multiple Content-Digest header values are not supported", policy.name(), keyId, algorithm, Map.of());
        }

        String digestHeader = digestHeaderValues.get(0);
        if (digestHeader == null || digestHeader.isBlank()) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Invalid Content-Digest header", policy.name(), keyId, algorithm, Map.of());
        }
        if (StructuredFieldSupport.hasTopLevelComma(digestHeader)) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Multiple Content-Digest entries are not supported", policy.name(), keyId, algorithm, Map.of("contentDigest", digestHeader));
        }

        int eq = digestHeader.indexOf('=');
        if (eq <= 0) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Invalid Content-Digest header", policy.name(), keyId, algorithm, Map.of("contentDigest", digestHeader));
        }

        String digestName = digestHeader.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String digestValue = digestHeader.substring(eq + 1).trim();
        if (!digestValue.startsWith(":") || !digestValue.endsWith(":")) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Digest value must be wrapped with ':'", policy.name(), keyId, algorithm, Map.of("contentDigest", digestHeader));
        }

        if (!policy.digestAlgorithm().equals(digestName)) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_UNSUPPORTED_DIGEST, "Digest algorithm is not allowed", policy.name(), keyId, algorithm, Map.of("digestAlgorithm", digestName));
        }

        if (contentDigestService.resolveJavaDigestName(digestName) == null) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_UNSUPPORTED_DIGEST, "Digest algorithm is not implemented", policy.name(), keyId, algorithm, Map.of("digestAlgorithm", digestName));
        }

        try {
            byte[] actual = contentDigestService.computeDigest(message.body(), digestName);
            byte[] expected = Base64.getDecoder().decode(digestValue.substring(1, digestValue.length() - 1));
            if (!java.security.MessageDigest.isEqual(actual, expected)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("digestAlgorithm", digestName);
                details.put("expectedDigest", Base64.getEncoder().encodeToString(expected));
                details.put("actualDigest", Base64.getEncoder().encodeToString(actual));
                return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Digest does not match payload", policy.name(), keyId, algorithm, details);
            }
            return null;
        } catch (Exception e) {
            return ValidationResult.invalid(SignatureValidationCode.SIG_INVALID_DIGEST, "Digest validation failed", policy.name(), keyId, algorithm, Map.of("error", e.getMessage()));
        }
    }
}
