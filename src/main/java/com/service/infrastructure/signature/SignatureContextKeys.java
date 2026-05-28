package com.service.infrastructure.signature;

/**
 * Request-context keys shared by the signature infrastructure and downstream technical concerns
 * such as legal archiving.
 */
public final class SignatureContextKeys {

    /**
     * Property containing the request-side {@code SignatureData} prepared by signature validation.
     */
    public static final String REQUEST_SIGNATURE_DATA = "http-signature.request-signature-data";

    /**
     * Property containing the response-side {@code SignatureData} prepared by response signing.
     */
    public static final String RESPONSE_SIGNATURE_DATA = "http-signature.response-signature-data";

    /**
     * Prevents instantiation of this constants holder.
     */
    private SignatureContextKeys() {
    }
}
