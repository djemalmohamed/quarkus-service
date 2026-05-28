package com.service.infrastructure.signature.validation.rest;

/**
 * Request-context keys exposed by the signature validation REST layer.
 */
public final class SignatureValidationRequestContextKeys {

    /**
     * Property containing the {@code ValidationReport} produced for the current inbound request.
     */
    public static final String VALIDATION_REPORT = SignatureValidationRequestContextKeys.class.getName() + ".validationReport";

    private SignatureValidationRequestContextKeys() {
    }
}
