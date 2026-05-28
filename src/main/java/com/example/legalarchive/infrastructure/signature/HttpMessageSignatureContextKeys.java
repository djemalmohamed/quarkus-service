package com.example.legalarchive.infrastructure.signature;

/**
 * Request-context keys used to exchange already prepared signature data between the upstream
 * signature layer and downstream consumers such as legal archiving.
 */
public final class HttpMessageSignatureContextKeys {

    public static final String REQUEST_SIGNATURE_DATA = "http-signature.request-signature-data";
    public static final String RESPONSE_SIGNATURE_DATA = "http-signature.response-signature-data";

    /**
     * Prevents instantiation of this constants holder.
     */
    private HttpMessageSignatureContextKeys() {
    }
}
