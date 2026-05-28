package com.playground.gateway.infrastructure.signature.validation.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Intermediate validation result used by specific sub-steps of the validation pipeline.
 *
 * <p>Unlike {@link ValidationReport}, this type is not meant to represent the full engine output.
 * It is used by lower-level services such as digest validation to return a precise failure that
 * can then be merged into the global report.</p>
 */
public record ValidationResult(
        boolean valid,
        SignatureValidationCode code,
        String message,
        String policy,
        String keyId,
        String algorithm,
        Map<String, Object> details
) {
    /**
     * Creates a successful intermediate validation result.
     *
     * @param policy applied policy name
     * @param keyId resolved key identifier
     * @param algorithm resolved signature algorithm
     * @param details optional diagnostics
     * @return immutable successful validation result
     */
    public static ValidationResult valid(String policy, String keyId, String algorithm, Map<String, Object> details) {
        return new ValidationResult(true, SignatureValidationCode.VALID, "Signature is valid", policy, keyId, algorithm, details);
    }

    /**
     * Creates a failed intermediate validation result.
     *
     * @param code failure code
     * @param message failure message
     * @param policy applied policy name
     * @param keyId resolved key identifier
     * @param algorithm resolved signature algorithm
     * @param details optional diagnostics
     * @return immutable failed validation result
     */
    public static ValidationResult invalid(SignatureValidationCode code, String message, String policy, String keyId, String algorithm, Map<String, Object> details) {
        return new ValidationResult(false, code, message, policy, keyId, algorithm, details == null ? Map.of() : new LinkedHashMap<>(details));
    }
}
