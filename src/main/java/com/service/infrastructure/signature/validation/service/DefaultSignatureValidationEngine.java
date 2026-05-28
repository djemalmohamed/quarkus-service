package com.service.infrastructure.signature.validation.service;

import com.service.infrastructure.signature.config.SignatureConfig;
import com.service.infrastructure.signature.rfc.CanonicalizationService;
import com.service.infrastructure.signature.rfc.CanonicalizationResult;
import com.service.infrastructure.signature.rfc.MissingCoveredComponentException;
import com.service.infrastructure.signature.rfc.SignatureComponentResolver;
import com.service.infrastructure.signature.rfc.SignatureInputParser;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.ParsedSignature;
import com.service.infrastructure.signature.rfc.model.ParsedSignatureInput;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.service.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import com.service.infrastructure.signature.shared.service.HeaderUtils;
import com.service.infrastructure.signature.shared.service.SignatureKeyMaterialResolver;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.service.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.service.infrastructure.signature.validation.model.SignatureData;
import com.service.infrastructure.signature.validation.model.SignatureValidationCode;
import com.service.infrastructure.signature.validation.model.ValidationIssue;
import com.service.infrastructure.signature.validation.model.ValidationReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default configuration-driven implementation of the signature validation engine.
 *
 * <p>This component coordinates the complete validation workflow for HTTP Message Signatures:
 * required header checks, signature input parsing, component validation,
 * digest verification, key resolution, certificate state checks and final cryptographic verification.</p>
 *
 * <p>Validation behavior is driven by the resolved policy and by the global mode configured for
 * the gateway. Depending on the mode, the engine can either stop at the first detected error
 * or collect all detectable issues in a single {@link ValidationReport}.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultSignatureValidationEngine implements SignatureValidationEngine {

    private static final Logger LOG = Logger.getLogger(DefaultSignatureValidationEngine.class);

    private final SignatureConfig config;

    private final SignatureInputParser signatureInputParser;

    private final DigestValidationService digestValidationService;

    private final SignatureKeyMaterialResolver keyMaterialResolver;

    private final CertificateRevocationValidationService certificateRevocationValidationService;

    private final SignatureComponentResolver signatureComponentResolver;

    private final CanonicalizationService canonicalizationService;

    private final CryptoVerificationService cryptoVerificationService;

    /**
     * Validates an inbound HTTP message using an already resolved validation policy.
     *
     * @param message inbound HTTP message to validate
     * @param policy policy already resolved by the caller
     * @return validation report containing success flag, issues and optional diagnostics
     */
    @Override
    public ValidationReport validate(IncomingHttpMessage message, ResolvedValidationPolicy policy) {
        return validate(SignatureContextMessage.standalone(message), policy);
    }

    /**
     * Validates an inbound signature context using an already resolved validation policy.
     *
     * @param context inbound signature context
     * @param policy policy already resolved by the caller
     * @return validation report containing success flag, issues and optional diagnostics
     */
    @Override
    public ValidationReport validate(SignatureContextMessage context, ResolvedValidationPolicy policy) {
        if (!config.enabled()) {
            return disabledReport();
        }

        try {
            return executeValidationPipeline(context, policy);
        } catch (Exception e) {
            return internalErrorReport(policy == null ? null : policy.name(), e);
        }
    }

    /**
     * Executes the full validation pipeline for the provided message and resolved policy.
     *
     * @param context inbound signature context to validate
     * @param policy resolved policy to apply
     * @return final validation report
     */
    private ValidationReport executeValidationPipeline(
            SignatureContextMessage signatureContext,
            ResolvedValidationPolicy policy
    ) {
        IncomingHttpMessage message = requireIncomingMessage(signatureContext);
        ValidationContext validationContext = createValidationContext(policy);

        // 1) Ensure the transport headers required by the endpoint policy are present.
        validateRequiredHeaders(validationContext, message);
        // 2) Parse Signature-Input and Signature into the internal RFC models.
        parseSignature(validationContext, message);
        // 3) Check that all policy-mandated covered components are actually signed.
        validateCoveredComponents(validationContext);
        // 4) Extract and validate the key identifier announced in Signature-Input.
        validateKeyIdentity(validationContext);
        // 5) Resolve the verification material associated with the announced key identifier.
        resolveKeyMaterial(validationContext);
        // 6) Reject messages using a signature algorithm not allowed by the policy.
        validateAlgorithm(validationContext);
        // 7) Apply certificate trust and revocation checks once key material is available.
        validatePostResolutionChecks(validationContext);
        // 8) Verify payload integrity through Content-Digest when required.
        validateDigest(validationContext, message);
        // 9) Rebuild the signature base, prepare reusable signature data and verify cryptographically.
        verifySignature(validationContext, signatureContext);
        // 10) Convert the accumulated validation state into the immutable public report.
        ValidationReport report = toReport(validationContext);
        logValidationReport(message, report);
        return report;
    }

    /**
     * Creates a mutable validation context used to carry intermediate state across pipeline steps.
     *
     * @param policy resolved validation policy
     * @return newly initialized validation context
     */
    private ValidationContext createValidationContext(ResolvedValidationPolicy policy) {
        return new ValidationContext(policy, resolveValidationMode(policy.failFast()));
    }

    /**
     * Validates the presence of transport headers required by the resolved policy.
     *
     * @param context mutable validation context
     * @param message inbound HTTP message
     */
    private void validateRequiredHeaders(ValidationContext context, IncomingHttpMessage message) {
        context.applyStepResult(checkRequiredHeaders(
                message.headers(),
                context.policy,
                context.issues,
                context.mode
        ));
    }

    /**
     * Parses the {@code Signature-Input} and {@code Signature} fields from the inbound message.
     *
     * @param context mutable validation context
     * @param message inbound HTTP message
     */
    private void parseSignature(ValidationContext context, IncomingHttpMessage message) {
        if (!context.canContinue()) {
            return;
        }

        try {
            captureSignatureHeaders(context, message);
            context.signatureInput = signatureInputParser.parseSignatureInput(message.headers());
            context.signature = signatureInputParser.parseSignature(message.headers(), context.signatureInput.label());
            context.algorithm = SignatureConstants.normalize(context.signatureInput.algorithm());
        } catch (SignatureInfrastructureException error) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    SignatureValidationCode.SIG_INVALID_FORMAT,
                    error.getMessage(),
                    SignatureConstants.HEADER_SIGNATURE
            ));
        }
    }

    /**
     * Checks that the covered components declared in {@code Signature-Input} satisfy the
     * resolved application policy.
     *
     * @param context mutable validation context
     */
    private void validateCoveredComponents(ValidationContext context) {
        if (!hasSignatureInput(context)) {
            return;
        }

        context.applyStepResult(validateRequiredComponents(
                context.signatureInput,
                context.policy,
                context.issues,
                context.mode
        ));
    }

    /**
     * Validates that the effective signature algorithm matches the application policy.
     *
     * @param context mutable validation context
     */
    private void validateAlgorithm(ValidationContext context) {
        if (!hasSignatureInput(context)) {
            return;
        }

        if (!context.policy.signatureAlgorithm().equals(context.algorithm)) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    SignatureValidationCode.SIG_UNSUPPORTED_ALGORITHM,
                    "Signature algorithm is not allowed",
                    "alg"
            ));
        }
    }

    /**
     * Validates the {@code Content-Digest} field against the actual received payload when required.
     *
     * <p>This check is intentionally performed after signature metadata parsing, key lookup and
     * trust validation so the implementation reads closer to the RFC verification flow while still
     * enforcing payload integrity before final cryptographic acceptance.</p>
     *
     * @param context mutable validation context
     * @param message inbound HTTP message
     */
    private void validateDigest(ValidationContext context, IncomingHttpMessage message) {
        if (!hasSignatureInput(context)) {
            return;
        }

        var digestFailure = digestValidationService.validate(message, context.policy, context.signatureInput.keyId(), context.algorithm);
        if (digestFailure != null) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    digestFailure.code(),
                    digestFailure.message(),
                    SignatureConstants.HEADER_CONTENT_DIGEST
            ));
            context.details.putAll(digestFailure.details());
        }
    }


    /**
     * Verifies that the parsed signature metadata carries a usable {@code keyid} parameter.
     *
     * @param context mutable validation context
     */
    private void validateKeyIdentity(ValidationContext context) {
        if (!hasSignatureInput(context)) {
            return;
        }

        if (context.signatureInput.keyId() == null || context.signatureInput.keyId().isBlank()) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    SignatureValidationCode.SIG_MISSING_KEY_ID,
                    "Missing keyId in Signature-Input",
                    "keyid"
            ));
        }
    }

    /**
     * Resolves the verification key material associated with the parsed {@code keyid}.
     *
     * @param context mutable validation context
     */
    private void resolveKeyMaterial(ValidationContext context) {
        if (!hasSignatureInput(context)) {
            return;
        }

        Optional<ResolvedKeyMaterial> resolvedKeyMaterial = keyMaterialResolver.resolveForVerification(context.signatureInput.keyId());
        if (resolvedKeyMaterial.isEmpty()) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    SignatureValidationCode.SIG_UNKNOWN_KEY,
                    "Unknown keyId",
                    "keyid"
            ));
            return;
        }
        context.keyMaterial = resolvedKeyMaterial.get();
    }

    /**
     * Runs checks that depend on the resolved key material, such as signer identity extraction
     * and certificate trust or revocation controls.
     *
     * @param context mutable validation context
     */
    private void validatePostResolutionChecks(ValidationContext context) {
        if (!hasSignatureInput(context)) {
            return;
        }

        if (hasResolvedKeyMaterial(context)) {
            context.signerDn = signerDn(context.keyMaterial);
            context.applyStepResult(validateCertificateState(context.keyMaterial, context.issues, context.mode));
        }
    }

    /**
     * Rebuilds the signature base and performs the final cryptographic verification.
     *
     * @param context mutable validation context
     * @param signatureContext inbound signature context
     */
    private void verifySignature(ValidationContext context, SignatureContextMessage signatureContext) {
        if (!hasSignatureInput(context) || context.signature == null) {
            return;
        }
        try {
            CanonicalizationResult canonicalizationResult = canonicalizationService.buildSignatureBase(signatureContext, context.signatureInput);
            context.componentValues = canonicalizationResult.componentValues();
            if (!hasResolvedKeyMaterial(context)) {
                return;
            }
            if (!context.policy.signatureAlgorithm().equals(context.algorithm)) {
                return;
            }
            boolean verified = cryptoVerificationService.verify(
                    context.signatureInput,
                    context.signature,
                    canonicalizationResult.signatureBase(),
                    context.keyMaterial
            );
            if (!verified) {
                addIssue(
                        context.issues,
                        context.mode,
                        SignatureValidationCode.SIG_INVALID_SIGNATURE,
                        "Signature verification failed",
                        SignatureConstants.HEADER_SIGNATURE
                );
            }
            context.details.put("components", context.signatureInput.components());
            if (config.logValidationDetails()) {
                context.details.put("resolvedKeyType", context.keyMaterial.type());
            }
        } catch (MissingCoveredComponentException error) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    SignatureValidationCode.SIG_MISSING_COMPONENT_VALUE,
                    error.getMessage(),
                    error.component()
            ));
        } catch (SignatureInfrastructureException error) {
            context.applyStepResult(addIssue(
                    context.issues,
                    context.mode,
                    SignatureValidationCode.SIG_INVALID_SIGNATURE,
                    "Signature verification failed",
                    SignatureConstants.HEADER_SIGNATURE
            ));
        }
    }

    /**
     * Ensures that the current message carried by the signature context is an inbound message.
     *
     * @param signatureContext inbound signature context
     * @return current inbound message extracted from the context
     */
    private IncomingHttpMessage requireIncomingMessage(SignatureContextMessage signatureContext) {
        if (signatureContext.currentMessage() instanceof IncomingHttpMessage message) {
            return message;
        }
        throw new IllegalArgumentException("Signature context current message must be an IncomingHttpMessage");
    }

    /**
     * Converts the mutable validation context into the public immutable validation report model.
     *
     * @param context mutable validation context
     * @return final validation report
     */
    private ValidationReport toReport(ValidationContext context) {
        SignatureData signatureData = toSignatureData(context);
        if (!context.issues.isEmpty()) {
            return ValidationReport.ko(
                    context.policy.name(),
                    context.mode.name(),
                    context.signatureInput == null ? null : context.signatureInput.keyId(),
                    context.signerDn,
                    context.algorithm,
                    signatureData,
                    context.issues,
                    context.details
            );
        }
        return ValidationReport.ok(
                context.policy.name(),
                context.mode.name(),
                context.signatureInput == null ? null : context.signatureInput.keyId(),
                context.signerDn,
                context.algorithm,
                signatureData,
                context.details
        );
    }

    /**
     * Captures the raw signature transport headers before any later validation step depends on them.
     *
     * @param context mutable validation context
     * @param message inbound HTTP message
     */
    private void captureSignatureHeaders(ValidationContext context, IncomingHttpMessage message) {
        putIfPresent(
                context.signatureHeaders,
                SignatureConstants.HEADER_SIGNATURE,
                HeaderUtils.firstHeader(message.headers(), SignatureConstants.HEADER_SIGNATURE)
        );
        putIfPresent(
                context.signatureHeaders,
                SignatureConstants.HEADER_SIGNATURE_INPUT,
                HeaderUtils.firstHeader(message.headers(), SignatureConstants.HEADER_SIGNATURE_INPUT)
        );
    }

    /**
     * Builds the immutable signature data view exposed in the final validation report.
     *
     * @param context mutable validation context
     * @return immutable signature data, or {@code null} when no signature transport data exists
     */
    private SignatureData toSignatureData(ValidationContext context) {
        if (context.signatureHeaders.isEmpty() && context.componentValues.isEmpty()) {
            return null;
        }
        String signatureHeader = context.signatureHeaders.get(SignatureConstants.HEADER_SIGNATURE);
        return new SignatureData(
                null == signatureHeader ? null : signatureHeader.getBytes(StandardCharsets.UTF_8),
                context.signatureHeaders.get(SignatureConstants.HEADER_SIGNATURE_INPUT),
                context.componentValues
        );
    }

    /**
     * Stores one header value only when it is actually present.
     *
     * @param target target mutable header map
     * @param name header name
     * @param value first resolved header value
     */
    private void putIfPresent(Map<String, String> target, String name, String value) {
        if (null != value && !value.isBlank()) {
            target.put(name, value);
        }
    }

    /**
     * Builds the standard report returned when the signature module is globally disabled.
     *
     * @return invalid validation report representing the disabled state
     */
    private ValidationReport disabledReport() {
        return ValidationReport.ko(
                "none",
                ValidationMode.FAIL_FAST.name(),
                null,
                null,
                null,
                List.of(new ValidationIssue(SignatureValidationCode.DISABLED, "Signature validation is disabled", SignatureConstants.HEADER_SIGNATURE)),
                Map.of()
        );
    }

    /**
     * Builds the standard report returned when the engine fails unexpectedly during validation.
     *
     * @param policyName policy name being applied when the error occurred
     * @param error unexpected failure
     * @return invalid validation report representing an internal error
     */
    private ValidationReport internalErrorReport(String policyName, Exception error) {
        LOG.errorf(error, "Signature validation engine failed for policy=%s", policyName == null ? "unknown" : policyName);
        return ValidationReport.ko(
                policyName == null ? "unknown" : policyName,
                ValidationMode.FAIL_FAST.name(),
                null,
                null,
                null,
                List.of(new ValidationIssue(SignatureValidationCode.SIG_INTERNAL_ERROR, "Validation engine failed", SignatureConstants.HEADER_SIGNATURE)),
                Map.of("error", error.getMessage())
        );
    }

    /**
     * Extracts the signer distinguished name from the resolved certificate when available.
     *
     * @param keyMaterial resolved verification key material
     * @return signer distinguished name or {@code null} when unavailable
     */
    private String signerDn(ResolvedKeyMaterial keyMaterial) {
        return keyMaterial == null || keyMaterial.certificate() == null
                ? null
                : keyMaterial.certificate().getSubjectX500Principal().getName();
    }


    /**
     * Indicates whether the pipeline may still use the parsed signature input safely.
     *
     * @param context mutable validation context
     * @return {@code true} when parsing succeeded and fail-fast has not stopped execution
     */
    private boolean hasSignatureInput(ValidationContext context) {
        return context.canContinue() && context.signatureInput != null;
    }

    /**
     * Indicates whether verification key material has been resolved and can be used.
     *
     * @param context mutable validation context
     * @return {@code true} when key material is available
     */
    private boolean hasResolvedKeyMaterial(ValidationContext context) {
        return hasSignatureInput(context) && context.keyMaterial != null;
    }

    /**
     * Indicates whether the engine has all prerequisites for final signature verification.
     *
     * @param context mutable validation context
     * @return {@code true} when both signature bytes and key material are available
     */
    private boolean hasResolvedSignature(ValidationContext context) {
        return hasResolvedKeyMaterial(context) && context.signature != null;
    }

    /**
     * Validates the signer certificate state before attempting cryptographic verification.
     *
     * <p>The current checks cover certificate validity dates and CRL-based revocation when the
     * feature is enabled. This keeps revoked or expired identities from being accepted even when
     * the signature bytes are otherwise correct.</p>
     *
     * @param keyMaterial resolved verification material
     * @param issues accumulated validation issues
     * @param mode effective validation mode
     * @return {@code true} when subsequent checks may continue
     */
    private boolean validateCertificateState(ResolvedKeyMaterial keyMaterial, List<ValidationIssue> issues, ValidationMode mode) {
        if (keyMaterial.certificate() == null) {
            return true;
        }

        try {
            keyMaterial.certificate().checkValidity();
        } catch (Exception error) {
            return addIssue(issues, mode, SignatureValidationCode.SIG_INVALID_CERTIFICATE, "Signer certificate is not valid anymore", "certificate");
        }

        if (!certificateRevocationValidationService.isTrusted(keyMaterial.certificate())) {
            return addIssue(issues, mode, SignatureValidationCode.SIG_UNTRUSTED_CERTIFICATE, "Signer certificate is not issued by a trusted CA", "certificate");
        }

        if (certificateRevocationValidationService.isRevoked(keyMaterial.certificate())) {
            return addIssue(issues, mode, SignatureValidationCode.SIG_REVOKED_CERTIFICATE, "Signer certificate is revoked", "certificate");
        }

        return true;
    }

    /**
     * Validates the presence of transport headers required by the resolved policy.
     *
     * @param headers inbound transport headers
     * @param policy resolved validation policy
     * @param issues accumulated validation issues
     * @param mode effective validation mode
     * @return {@code true} when subsequent checks may continue
     */
    private boolean checkRequiredHeaders(
            Map<String, List<String>> headers,
            ResolvedValidationPolicy policy,
            List<ValidationIssue> issues,
            ValidationMode mode
    ) {
        for (String header : policy.requiredHeaders()) {
            if (HeaderUtils.firstHeader(headers, header) == null) {
                if (!addIssue(
                        issues,
                        mode,
                        SignatureValidationCode.SIG_MISSING_HEADER,
                        "Missing required header: " + header,
                        header
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Validates the set of covered components announced by {@code Signature-Input}.
     *
     * @param signatureInput parsed signature input
     * @param policy resolved validation policy
     * @param issues accumulated validation issues
     * @param mode effective validation mode
     * @return {@code true} when subsequent checks may continue
     */
    private boolean validateRequiredComponents(
            ParsedSignatureInput signatureInput,
            ResolvedValidationPolicy policy,
            List<ValidationIssue> issues,
            ValidationMode mode
    ) {
        Set<String> actualComponents = new HashSet<>(signatureInput.components().size());
        signatureInput.components().forEach(component -> actualComponents.add(component.identifier()));

        for (CoveredComponent requiredComponent : policy.requiredComponents()) {
            if (!actualComponents.contains(requiredComponent.identifier())) {
                if (!addIssue(
                        issues,
                        mode,
                        SignatureValidationCode.SIG_MISSING_COMPONENT,
                        "Missing signed component: " + requiredComponent.identifier(),
                        requiredComponent.identifier()
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Records a validation issue and decides whether the pipeline should stop immediately.
     *
     * @param issues accumulated validation issues
     * @param mode effective validation mode
     * @param code issue code
     * @param message issue message
     * @param field failing field
     * @return {@code true} when the pipeline may continue
     */
    private boolean addIssue(
            List<ValidationIssue> issues,
            ValidationMode mode,
            SignatureValidationCode code,
            String message,
            String field
    ) {
        issues.add(new ValidationIssue(code, message, field));
        return mode != ValidationMode.FAIL_FAST;
    }

    /**
     * Emits a safe warning log for failed validations without storing any in-memory counters.
     *
     * @param message inbound HTTP message that was validated
     * @param report final validation report
     */
    private void logValidationReport(IncomingHttpMessage message, ValidationReport report) {
        if (report == null || report.valid()) {
            return;
        }

        LOG.warnf(
                "Signature validation failed: method=%s path=%s policy=%s keyId=%s signerDn=%s issues=%s",
                safe(message == null ? null : message.method()),
                safe(message == null ? null : message.path()),
                safe(report.policy()),
                safe(report.keyId()),
                safe(report.signerDn()),
                issueSummary(report)
        );
    }

    /**
     * Formats all validation issues into a compact single-line representation suitable for logs.
     *
     * @param report final validation report
     * @return formatted issue summary
     */
    private String issueSummary(ValidationReport report) {
        return report.issues().stream()
                .map(this::formatIssue)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Formats a single validation issue for diagnostic logs.
     *
     * @param issue validation issue to format
     * @return compact issue representation
     */
    private String formatIssue(ValidationIssue issue) {
        String field = issue.field() == null || issue.field().isBlank() ? "n/a" : issue.field();
        return issue.code().name() + "(" + field + "): " + issue.message();
    }

    /**
     * Replaces null values before rendering log lines.
     *
     * @param value raw string value
     * @return original value or {@code "n/a"} when absent
     */
    private String safe(String value) {
        return value == null ? "n/a" : value;
    }

    /**
     * Converts the boolean fail-fast flag into the public report mode label.
     *
     * @param failFast resolved policy flag
     * @return report mode name
     */
    private ValidationMode resolveValidationMode(boolean failFast) {
        return failFast ? ValidationMode.FAIL_FAST : ValidationMode.COLLECT_ALL;
    }

    private static final class ValidationContext {
        private final ResolvedValidationPolicy policy;
        private final ValidationMode mode;
        private final List<ValidationIssue> issues = new ArrayList<>();
        private final Map<String, Object> details = new LinkedHashMap<>();
        private final Map<String, String> signatureHeaders = new LinkedHashMap<>();
        private boolean continueChecks = true;
        private ParsedSignatureInput signatureInput;
        private ParsedSignature signature;
        private Map<String, String> componentValues = Map.of();
        private String algorithm;
        private String signerDn;
        private ResolvedKeyMaterial keyMaterial;

        /**
         * Creates a new mutable validation context for one engine invocation.
         *
         * @param policy resolved validation policy
         * @param mode effective validation mode
         */
        private ValidationContext(ResolvedValidationPolicy policy, ValidationMode mode) {
            this.policy = policy;
            this.mode = mode;
        }

        /**
         * Indicates whether the validation pipeline may still execute subsequent steps.
         *
         * @return {@code true} when no fail-fast stop condition has been triggered
         */
        private boolean canContinue() {
            return continueChecks;
        }

        /**
         * Applies the result of one validation step to the global pipeline state.
         *
         * <p>Once the pipeline is stopped, later successful step results cannot re-enable it.</p>
         *
         * @param stepResult outcome of the latest validation step
         */
        private void applyStepResult(boolean stepResult) {
            this.continueChecks = this.continueChecks && stepResult;
        }
    }

    private enum ValidationMode {
        FAIL_FAST,
        COLLECT_ALL
    }
}
