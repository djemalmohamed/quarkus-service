package com.example.legalarchive.infrastructure.adapters.out.http;

import com.example.legalarchive.infrastructure.signature.model.SignatureData;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import java.net.URI;
import java.util.Objects;

/**
 * Immutable outbound HTTP request description used by the Vert.x legal-archiving wrapper.
 *
 * @param method the HTTP method to invoke
 * @param uri the absolute target URI
 * @param headers the outbound HTTP headers
 * @param body the outbound HTTP body
 * @param operation an optional business operation override
 * @param signatureData the signature data already prepared by the outbound signing flow when available
 */
public record VertxReactiveHttpRequest(
        HttpMethod method,
        URI uri,
        MultiMap headers,
        Buffer body,
        String operation,
        SignatureData signatureData) {

    /**
     * Creates an outbound request without pre-parsed signature material.
     *
     * @param method the HTTP method to invoke
     * @param uri the absolute target URI
     * @param headers the outbound HTTP headers
     * @param body the outbound HTTP body
     * @param operation an optional business operation override
     */
    public VertxReactiveHttpRequest(
            HttpMethod method,
            URI uri,
            MultiMap headers,
            Buffer body,
            String operation) {
        this(method, uri, headers, body, operation, null);
    }

    public VertxReactiveHttpRequest {
        method = Objects.requireNonNull(method, "method must not be null");
        uri = Objects.requireNonNull(uri, "uri must not be null");
        headers = copyHeaders(headers);
        body = null == body ? Buffer.buffer() : body.copy();
    }

    @Override
    public MultiMap headers() {
        return copyHeaders(headers);
    }

    @Override
    public Buffer body() {
        return body.copy();
    }

    /**
     * @return {@code true} when the outbound request contains a body
     */
    public boolean hasBody() {
        return body.length() > 0;
    }

    /**
     * Creates a defensive copy of the outbound headers to preserve immutability.
     *
     * @param source the source headers to copy
     * @return a case-insensitive copied header map
     */
    private static MultiMap copyHeaders(MultiMap source) {
        MultiMap copy = MultiMap.caseInsensitiveMultiMap();
        if (null != source) {
            copy.addAll(source);
        }
        return copy;
    }
}
