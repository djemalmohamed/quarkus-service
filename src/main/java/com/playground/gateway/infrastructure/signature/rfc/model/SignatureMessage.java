package com.playground.gateway.infrastructure.signature.rfc.model;

/**
 * Minimal RFC-facing view of an HTTP message used for canonicalization and component resolution.
 *
 * <p>This interface deliberately exposes only the protocol data needed by the HTTP Message
 * Signatures implementation so the RFC package can remain independent from validation and
 * generation transport models.</p>
 */
public interface SignatureMessage {

    String method();

    String path();

    String query();

    String authority();

    Integer status();

    byte[] body();

    String firstHeader(String name);

    String targetUri();

    default int bodyLength() {
        return body() == null ? 0 : body().length;
    }
}
