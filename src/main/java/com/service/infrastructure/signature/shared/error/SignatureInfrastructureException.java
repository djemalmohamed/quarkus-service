package com.service.infrastructure.signature.shared.error;

/**
 * Signals a technical failure while parsing, generating or verifying message signatures.
 *
 * <p>This exception covers failures caused by malformed signature headers, serialization
 * problems, cryptographic primitives, file access or other infrastructure concerns that are
 * outside the application use cases themselves.</p>
 */
public class SignatureInfrastructureException extends RuntimeException {

    /**
     * Creates a new infrastructure exception.
     *
     * @param message human-readable description of the technical failure
     */
    public SignatureInfrastructureException(String message) {
        super(message);
    }

    /**
     * Creates a new infrastructure exception with a root cause.
     *
     * @param message human-readable description of the technical failure
     * @param cause underlying cause
     */
    public SignatureInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
