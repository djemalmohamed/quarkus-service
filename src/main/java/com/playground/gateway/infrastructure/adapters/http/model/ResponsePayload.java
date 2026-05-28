package com.playground.gateway.infrastructure.adapters.http.model;

import com.playground.gateway.infrastructure.signature.validation.model.ValidationIssue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mutable technical response model returned by the reusable outbound HTTP adapter.
 *
 * <p>The payload captures transport-level information, generated signature headers and the
 * validation outcome of any synchronous signed response. Business adapters map this structure
 * to their own domain result objects.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ResponsePayload {
    private Integer status;
    private String duration;
    private String body;
    private String error;
    private Map<String, List<String>> headers;
    private String requestSignatureEndpoint;
    private String responseSignatureEndpoint;
    private Map<String, String> generatedSignatureHeaders;
    private List<ValidationIssue> responseSignatureIssues = new ArrayList<>();
}
