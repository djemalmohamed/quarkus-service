package com.service.infrastructure.signature.generation.model;

import com.service.infrastructure.signature.rfc.model.SignatureMessage;
import com.service.infrastructure.signature.shared.service.HeaderUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Internal transport-agnostic view of an outbound HTTP message to be signed.
 */
public record OutgoingHttpMessage(
        String method,
        URI uri,
        Map<String, String> headers,
        byte[] body,
        Integer status
) implements SignatureMessage {

    @Override
    public String path() {
        return uri == null || uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
    }

    @Override
    public String query() {
        return uri == null ? null : uri.getRawQuery();
    }

    @Override
    public String authority() {
        return uri == null ? "" : uri.getAuthority();
    }

    @Override
    public String firstHeader(String name) {
        return HeaderUtils.firstHeaderValue(headers, name);
    }

    @Override
    public String targetUri() {
        if (uri == null) {
            return "";
        }

        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        String prefix = scheme == null || authority == null ? "" : scheme.toLowerCase(Locale.ROOT) + "://" + authority;
        String querySuffix = uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? "" : "?" + uri.getRawQuery();
        return prefix + path() + querySuffix;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OutgoingHttpMessage that)) {
            return false;
        }
        return Objects.equals(method, that.method)
                && Objects.equals(uri, that.uri)
                && Objects.equals(headers, that.headers)
                && Arrays.equals(body, that.body)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(method, uri, headers, status);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "OutgoingHttpMessage{"
                + "method='" + method + '\''
                + ", uri=" + uri
                + ", headers=" + headers
                + ", bodyLength=" + (body == null ? 0 : body.length)
                + ", status=" + status
                + '}';
    }
}
