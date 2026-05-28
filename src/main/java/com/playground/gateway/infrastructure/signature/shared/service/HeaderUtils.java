package com.playground.gateway.infrastructure.signature.shared.service;

import com.playground.gateway.infrastructure.signature.shared.SignatureConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small utility methods for case-insensitive header lookups.
 *
 * <p>The gateway uses simple maps for inbound and outbound headers to stay framework-agnostic
 * inside the signature engine. This helper centralizes the case-insensitive access logic so the
 * rest of the code can focus on protocol concerns instead of map traversal details.</p>
 */
public final class HeaderUtils {

    private HeaderUtils() {
    }

    /**
     * Returns the first value of a multi-valued header map.
     *
     * @param headers source header map
     * @param name header name to resolve
     * @return first header value when present, otherwise {@code null}
     */
    public static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        String normalizedName = SignatureConstants.normalize(name);
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (SignatureConstants.normalize(entry.getKey()).equals(normalizedName)) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }

    /**
     * Returns all values carried by a case-insensitive header name.
     *
     * @param headers source header map
     * @param name header name to resolve
     * @return ordered matching values
     */
    public static List<String> matchingHeaderValues(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }

        String normalizedName = SignatureConstants.normalize(name);
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!SignatureConstants.normalize(entry.getKey()).equals(normalizedName)) {
                continue;
            }
            if (entry.getValue() != null) {
                values.addAll(entry.getValue());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Returns a single-valued header from a flat header map.
     *
     * @param headers source header map
     * @param name header name to resolve
     * @return header value when present, otherwise {@code null}
     */
    public static String firstHeaderValue(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        String normalizedName = SignatureConstants.normalize(name);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (SignatureConstants.normalize(entry.getKey()).equals(normalizedName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
