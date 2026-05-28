package com.playground.gateway.infrastructure.adapters.http.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable technical request model consumed by the reusable outbound HTTP adapter.
 *
 * <p>The object intentionally stays infrastructure-oriented. It can be built from a business
 * adapter such as payments, enriched with target defaults and finally transformed into a real
 * HTTP request by the low-level transport adapter.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestPayload {
    private String target;
    private String method;
    private String url;
    private String body;
    private String requestSignatureEndpoint;
    private String responseSignatureEndpoint;
    private Map<String, String> headers = new LinkedHashMap<>();
}
