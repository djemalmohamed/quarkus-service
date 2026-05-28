package com.playground.gateway.infrastructure.signature.validation.model;

/**
 * Stable set of machine-readable validation result codes exposed by the signature module.
 */
public enum SignatureValidationCode {
    VALID,
    DISABLED,
    SIG_MISSING_HEADER,
    SIG_INVALID_FORMAT,
    SIG_MISSING_KEY_ID,
    SIG_MISSING_DIGEST,
    SIG_INVALID_DIGEST,
    SIG_UNSUPPORTED_DIGEST,
    SIG_UNSUPPORTED_ALGORITHM,
    SIG_MISSING_COMPONENT,
    SIG_MISSING_COMPONENT_VALUE,
    SIG_UNKNOWN_KEY,
    SIG_INVALID_SIGNATURE,
    SIG_INVALID_CERTIFICATE,
    SIG_UNTRUSTED_CERTIFICATE,
    SIG_REVOKED_CERTIFICATE,
    SIG_INTERNAL_ERROR
}
