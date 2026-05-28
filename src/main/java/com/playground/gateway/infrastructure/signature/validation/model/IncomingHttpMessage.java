package com.playground.gateway.infrastructure.signature.validation.model;

import com.playground.gateway.infrastructure.signature.rfc.model.SignatureMessage;
import com.playground.gateway.infrastructure.signature.shared.service.HeaderUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal transport-agnostic view of an inbound HTTP message.
 *
 * <p>The validation engine consumes this record instead of framework-specific request objects so
 * it remains easy to test, reuse and evolve independently from JAX-RS or any other transport.</p>
 */
public record IncomingHttpMessage(
        String method,
        String path,
        String query,
        String authority,
        Map<String, List<String>> headers,
        byte[] body,
        Integer status
) implements SignatureMessage {

    @Override
    public String firstHeader(String name) {
        return HeaderUtils.firstHeader(headers, name);
    }

    @Override
    public String targetUri() {
        String resolvedPath = path == null || path.isBlank() ? "/" : path;
        String querySuffix = query == null || query.isBlank() ? "" : "?" + query;
        return resolvedPath + querySuffix;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IncomingHttpMessage that)) {
            return false;
        }
        return Objects.equals(method, that.method)
                && Objects.equals(path, that.path)
                && Objects.equals(query, that.query)
                && Objects.equals(authority, that.authority)
                && Objects.equals(headers, that.headers)
                && Arrays.equals(body, that.body)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(method, path, query, authority, headers, status);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "IncomingHttpMessage{"
                + "method='" + method + '\''
                + ", path='" + path + '\''
                + ", query='" + query + '\''
                + ", authority='" + authority + '\''
                + ", headers=" + headers
                + ", bodyLength=" + (body == null ? 0 : body.length)
                + ", status=" + status
                + '}';
    }
}
