package com.playground.gateway.infrastructure.signature.validation.service;

import com.playground.gateway.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.playground.gateway.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.playground.gateway.infrastructure.signature.validation.model.ValidationReport;
import com.playground.gateway.infrastructure.signature.rfc.model.SignatureContextMessage;

/**
 * Contract for validating inbound HTTP messages against a signature policy.
 *
 * <p>The validation engine is responsible for applying the configuration-driven HTTP Message
 * Signatures rules for a given message and returning a structured report that callers can use
 * to reject, log or inspect invalid requests and responses.</p>
 */
public interface SignatureValidationEngine {

    /**
     * Validates the given HTTP message using an already resolved validation policy.
     *
     * @param message inbound HTTP message to validate
     * @param policy policy already resolved by the caller
     * @return structured validation report containing success flag, issues and optional diagnostics
     */
    ValidationReport validate(IncomingHttpMessage message, ResolvedValidationPolicy policy);

    /**
     * Validates the given signature context with an already resolved policy.
     *
     * @param context inbound signature context containing the current message and an optional
     *                associated request for {@code ;req} components
     * @param policy policy already resolved by the caller
     * @return structured validation report containing success flag, issues and optional diagnostics
     */
    ValidationReport validate(SignatureContextMessage context, ResolvedValidationPolicy policy);

}
