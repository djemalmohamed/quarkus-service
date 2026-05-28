package com.playground.gateway.infrastructure.signature.shared.error;

/**
 * Signals an invalid or incomplete signature configuration.
 *
 * <p>This exception is intended for startup-time or runtime configuration problems such as
 * unknown policies, unsupported configured algorithms, missing key material or inconsistent
 * rule declarations. It should not be used for partner input validation failures.</p>
 */
public class SignatureConfigurationException extends RuntimeException {

    /**
     * Creates a new configuration exception.
     *
     * @param message human-readable description of the configuration problem
     */
    public SignatureConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new configuration exception with a root cause.
     *
     * @param message human-readable description of the configuration problem
     * @param cause underlying cause
     */
    public SignatureConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
