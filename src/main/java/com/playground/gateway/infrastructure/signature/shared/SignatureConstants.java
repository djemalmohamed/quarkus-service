package com.playground.gateway.infrastructure.signature.shared;

import java.util.Locale;

/**
 * Centralizes signature-related literals shared by validation, generation and HTTP adapters.
 */
public final class SignatureConstants {

    public static final String MATCHER_ALL = "ALL";

    public static final String HEADER_SIGNATURE = "signature";
    public static final String HEADER_SIGNATURE_INPUT = "signature-input";
    public static final String HEADER_CONTENT_DIGEST = "content-digest";
    public static final String HEADER_CONTENT_LENGTH = "content-length";
    public static final String HEADER_REQUEST_ID = "request-id";
    public static final String HEADER_REQUEST_TIMESTAMP = "request-timestamp";
    public static final String HEADER_RESPONSE_TIMESTAMP = "response-timestamp";

    public static final String HTTP_HEADER_SIGNATURE = "Signature";
    public static final String HTTP_HEADER_SIGNATURE_INPUT = "Signature-Input";
    public static final String HTTP_HEADER_CONTENT_DIGEST = "Content-Digest";
    public static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String HTTP_HEADER_REQUEST_ID = "Request-ID";
    public static final String HTTP_HEADER_REQUEST_TIMESTAMP = "Request-Timestamp";
    public static final String HTTP_HEADER_RESPONSE_TIMESTAMP = "Response-Timestamp";

    public static final String NONCE_CHECK_FORMAT_ONLY = "FORMAT_ONLY";

    public static final String COMPONENT_METHOD = "@method";
    public static final String COMPONENT_PATH = "@path";
    public static final String COMPONENT_QUERY = "@query";
    public static final String COMPONENT_AUTHORITY = "@authority";
    public static final String COMPONENT_STATUS = "@status";
    public static final String COMPONENT_TARGET_URI = "@target-uri";
    public static final String COMPONENT_SIGNATURE_PARAMS = "@signature-params";

    public static final String INTERNAL_QUARKUS_PATH_PREFIX = "/q/";

    public static final String DIGEST_SHA_256 = "sha-256";
    public static final String DIGEST_SHA_512 = "sha-512";
    public static final String JCA_DIGEST_SHA_256 = "SHA-256";
    public static final String JCA_DIGEST_SHA_512 = "SHA-512";

    public static final String ALGORITHM_ECDSA_P256_SHA_256 = "ecdsa-p256-sha256";

    private SignatureConstants() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
