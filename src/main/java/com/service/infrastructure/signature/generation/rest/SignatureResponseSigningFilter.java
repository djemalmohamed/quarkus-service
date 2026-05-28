package com.service.infrastructure.signature.generation.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.rfc.MissingCoveredComponentException;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.generation.service.SignatureGenerationService;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound REST response filter responsible for optionally signing gateway responses.
 *
 * <p>The filter resolves the dedicated inbound response-generation policy family and signs
 * business responses when one policy applies. Internal Quarkus endpoints are ignored so that
 * operational tooling remains unaffected by business-facing signature rules.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SignatureResponseSigningFilter {

    private final SignatureGenerationService signatureGenerationService;

    private final SignaturePolicyResolver policyResolver;

    private final ObjectMapper objectMapper;

    /**
     * Intercepts outbound REST responses and applies the inbound response-generation policy when configured.
     *
     * @param requestContext inbound JAX-RS request context associated with the response
     * @param responseContext outbound JAX-RS response context to enrich with signature headers
     */
    // Disabled temporarily in favor of SignatureResponseSigningWriterInterceptor.
    // @ServerResponseFilter(priority = Priorities.HEADER_DECORATOR)
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (isInternalPath(requestContext.getUriInfo().getRequestUri().getPath())) {
            return;
        }

        byte[] body = responseBody(responseContext);
        Map<String, String> headers = responseHeaders(responseContext);
        OutgoingHttpMessage relatedRequest = requestMessage(requestContext);
        OutgoingHttpMessage message = new OutgoingHttpMessage(
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri(),
                headers,
                body,
                responseContext.getStatus()
        );
        Optional<ResolvedGenerationPolicy> resolvedPolicy = policyResolver.resolveInboundGeneration(null, message);
        if (resolvedPolicy.isEmpty()) {
            return;
        }

        ensureGeneratedResponseHeaders(responseContext, headers, resolvedPolicy.get());

        SignatureGenerationResult result;
        try {
            result = signatureGenerationService.sign(
                    SignatureContextMessage.withAssociatedRequest(message, relatedRequest),
                    resolvedPolicy.get()
            );
        } catch (MissingCoveredComponentException error) {
            return;
        }

        result.headers().forEach((name, value) -> responseContext.getHeaders().putSingle(name, value));
    }

    /**
     * Materializes the current response headers into a flat map understood by the signature module.
     *
     * @param responseContext outgoing response context
     * @return single-valued response headers
     */
    private Map<String, String> responseHeaders(ContainerResponseContext responseContext) {
        Map<String, String> headers = new LinkedHashMap<>();
        responseContext.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty() && values.get(0) != null) {
                headers.put(name, String.valueOf(values.get(0)));
            }
        });
        return headers;
    }

    /**
     * Adds response-local headers that must exist before the signature is computed.
     *
     * @param responseContext outgoing response context
     * @param headers mutable response header map used for signature generation
     * @param policy resolved inbound response-generation policy
     */
    private void ensureGeneratedResponseHeaders(
            ContainerResponseContext responseContext,
            Map<String, String> headers,
            ResolvedGenerationPolicy policy
    ) {
        for (CoveredComponent component : policy.components()) {
            if (component.requestComponent() || !SignatureConstants.HEADER_RESPONSE_TIMESTAMP.equals(component.name())) {
                continue;
            }
            if (firstHeaderValue(headers, SignatureConstants.HEADER_RESPONSE_TIMESTAMP) != null) {
                continue;
            }
            String responseTimestamp = Instant.now().toString();
            headers.put(SignatureConstants.HTTP_HEADER_RESPONSE_TIMESTAMP, responseTimestamp);
            if (responseContext.getHeaders().getFirst(SignatureConstants.HTTP_HEADER_RESPONSE_TIMESTAMP) == null) {
                responseContext.getHeaders().putSingle(SignatureConstants.HTTP_HEADER_RESPONSE_TIMESTAMP, responseTimestamp);
            }
        }
    }

    /**
     * Builds the detached request view used by response components declared with {@code ;req}.
     *
     * @param requestContext inbound JAX-RS request context associated with the response
     * @return request view exposing the request headers and target metadata
     */
    private OutgoingHttpMessage requestMessage(ContainerRequestContext requestContext) {
        Map<String, String> headers = new LinkedHashMap<>();
        requestContext.getHeaders().forEach((name, values) -> {
            if (name != null && values != null && !values.isEmpty() && values.get(0) != null) {
                headers.put(name, String.valueOf(values.get(0)));
            }
        });

        return new OutgoingHttpMessage(
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri(),
                headers,
                new byte[0],
                null
        );
    }

    /**
     * Serializes the outgoing response entity into bytes so it can be digested and signed.
     *
     * @param responseContext JAX-RS response context
     * @return serialized response body bytes
     */
    private byte[] responseBody(ContainerResponseContext responseContext) {
        Object entity = responseContext.getEntity();
        if (entity == null) {
            return new byte[0];
        }
        if (entity instanceof byte[] bytes) {
            return bytes;
        }
        if (entity instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.writeValueAsBytes(entity);
        } catch (Exception e) {
            throw new SignatureInfrastructureException("Unable to serialize response for signature", e);
        }
    }

    /**
     * Identifies Quarkus internal endpoints that must stay outside business signature rules.
     *
     * @param path request path
     * @return {@code true} when the path belongs to Quarkus internal tooling
     */
    private boolean isInternalPath(String path) {
        return path != null && path.startsWith(SignatureConstants.INTERNAL_QUARKUS_PATH_PREFIX);
    }

    /**
     * Resolves the first response header value matching a normalized header name.
     *
     * @param headers flat response header map
     * @param headerName normalized header name to search for
     * @return first matching header value, or {@code null} when absent
     */
    private String firstHeaderValue(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (SignatureConstants.normalize(entry.getKey()).equals(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

}
