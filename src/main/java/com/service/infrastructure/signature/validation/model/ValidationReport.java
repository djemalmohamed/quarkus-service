package com.service.infrastructure.signature.validation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Final validation outcome returned by the inbound signature engine.
 *
 * <p>The report keeps both a machine-friendly view of the outcome and enough diagnostics to be
 * exposed to logs, monitoring or callers depending on the active policy and environment.</p>
 */
public record ValidationReport(
        boolean valid,
        String policy,
        String mode,
        String keyId,
        String signerDn,
        String algorithm,
        SignatureData signatureData,
        List<ValidationIssue> issues,
        Map<String, Object> details
) {
    /**
     * Normalizes mutable inputs into immutable report state.
     */
    public ValidationReport {
        signatureData = null == signatureData ? null : signatureData.copy();
        issues = null == issues ? List.of() : List.copyOf(issues);
        details = null == details ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /**
     * Creates a successful validation report.
     *
     * @param policy applied policy name
     * @param mode effective validation mode
     * @param keyId resolved key identifier
     * @param algorithm resolved signature algorithm
     * @param details optional diagnostics
     * @return immutable success report
     */
    public static ValidationReport ok(String policy, String mode, String keyId, String signerDn, String algorithm, Map<String, Object> details) {
        return ok(policy, mode, keyId, signerDn, algorithm, null, details);
    }

    /**
     * Creates a successful validation report enriched with signature archive data.
     *
     * @param policy applied policy name
     * @param mode effective validation mode
     * @param keyId resolved key identifier
     * @param signerDn resolved signer distinguished name
     * @param algorithm resolved signature algorithm
     * @param signatureData signature material prepared for downstream archiving
     * @param details optional diagnostics
     * @return immutable success report
     */
    public static ValidationReport ok(
            String policy,
            String mode,
            String keyId,
            String signerDn,
            String algorithm,
            SignatureData signatureData,
            Map<String, Object> details
    ) {
        return new ValidationReport(true, policy, mode, keyId, signerDn, algorithm, signatureData, List.of(), details);
    }

    /**
     * Creates a failed validation report.
     *
     * @param policy applied policy name
     * @param mode effective validation mode
     * @param keyId resolved key identifier
     * @param algorithm resolved signature algorithm
     * @param issues collected validation issues
     * @param details optional diagnostics
     * @return immutable failure report
     */
    public static ValidationReport ko(String policy, String mode, String keyId, String signerDn, String algorithm, List<ValidationIssue> issues, Map<String, Object> details) {
        return ko(policy, mode, keyId, signerDn, algorithm, null, issues, details);
    }

    /**
     * Creates a failed validation report enriched with signature archive data.
     *
     * @param policy applied policy name
     * @param mode effective validation mode
     * @param keyId resolved key identifier
     * @param signerDn resolved signer distinguished name
     * @param algorithm resolved signature algorithm
     * @param signatureData signature material prepared for downstream archiving
     * @param issues collected validation issues
     * @param details optional diagnostics
     * @return immutable failure report
     */
    public static ValidationReport ko(
            String policy,
            String mode,
            String keyId,
            String signerDn,
            String algorithm,
            SignatureData signatureData,
            List<ValidationIssue> issues,
            Map<String, Object> details
    ) {
        return new ValidationReport(false, policy, mode, keyId, signerDn, algorithm, signatureData, issues, details);
    }
}
