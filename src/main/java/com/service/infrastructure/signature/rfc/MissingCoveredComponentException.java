package com.service.infrastructure.signature.rfc;

import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;

/**
 * Signals that a component declared in the RFC signature coverage cannot be resolved from the message.
 */
public class MissingCoveredComponentException extends SignatureInfrastructureException {

    private final String component;

    public MissingCoveredComponentException(String component) {
        super("Covered component is missing from message: " + component);
        this.component = component;
    }

    public String component() {
        return component;
    }
}
