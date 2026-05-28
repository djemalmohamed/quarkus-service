package com.service.infrastructure.signature.validation.error;

import com.service.infrastructure.signature.validation.model.ValidationReport;

/**
 * Raised when inbound signature validation must immediately reject the current request.
 */
public class SignatureValidationException extends RuntimeException {

    private final ValidationReport report;

    public SignatureValidationException(ValidationReport report) {
        super("Signature validation failed");
        this.report = report;
    }

    public ValidationReport report() {
        return report;
    }
}
