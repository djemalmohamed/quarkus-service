package com.service.infrastructure.signature.generation.rest;

import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.generation.service.SignatureGenerationService;
import com.service.infrastructure.signature.rfc.MissingCoveredComponentException;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Alternative response signing hook that runs during entity serialization.
 *
 * <p>This interceptor is intentionally kept alongside the existing response filter so we can
 * compare both execution points. It captures the final serialized body, ensures late response
 * headers such as {@code Content-Type} and {@code Content-Length} are available, then applies
 * the same response-signing logic as the filter.</p>
 */
@Provider
@ApplicationScoped
@Priority(Priorities.USER + 1000)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SignatureResponseSigningWriterInterceptor implements WriterInterceptor {

    private final SignatureGenerationService signatureGenerationService;

    private final SignaturePolicyResolver policyResolver;

    private final CurrentVertxRequest currentVertxRequest;

    /**
     * Captures the final serialized response body and signs it when one inbound response policy applies.
     *
     * @param context JAX-RS writer interceptor context
     * @throws IOException when writing the response body fails
     * @throws WebApplicationException when downstream serialization fails
     */
    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        OutputStream originalOutputStream = context.getOutputStream();
        ByteArrayOutputStream capturedOutputStream = new ByteArrayOutputStream();
        context.setOutputStream(capturedOutputStream);
        context.proceed();

        byte[] body = capturedOutputStream.toByteArray();
        context.setOutputStream(originalOutputStream);

        RoutingContext routingContext = currentVertxRequest == null ? null : currentVertxRequest.getCurrent();
        if (routingContext == null || isInternalPath(routingContext.request().path())) {
            originalOutputStream.write(body);
            return;
        }

        ensureTransportHeaders(context, body);

        HttpServerRequest request = routingContext.request();
        URI requestUri = URI.create(request.absoluteURI());
        String method = request.method().name();
        int status = routingContext.response().getStatusCode();

        Map<String, String> previewHeaders = responseHeaders(context);
        OutgoingHttpMessage previewMessage = new OutgoingHttpMessage(
                method,
                requestUri,
                previewHeaders,
                body,
                status
        );
        Optional<ResolvedGenerationPolicy> resolvedPolicy = policyResolver.resolveInboundGeneration(null, previewMessage);
        if (resolvedPolicy.isPresent()) {
            ensureGeneratedResponseHeaders(context, previewHeaders, resolvedPolicy.get());

            Map<String, String> headers = responseHeaders(context);
            OutgoingHttpMessage message = new OutgoingHttpMessage(
                    method,
                    requestUri,
                    headers,
                    body,
                    status
            );
            OutgoingHttpMessage relatedRequest = requestMessage(request);
            try {
                SignatureGenerationResult result = signatureGenerationService.sign(
                        SignatureContextMessage.withAssociatedRequest(message, relatedRequest),
                        resolvedPolicy.get()
                );
                result.headers().forEach((name, value) -> context.getHeaders().putSingle(name, value));
            } catch (MissingCoveredComponentException ignored) {
                // Keep the response unsigned when late transport headers are still unavailable.
            }
        }

        originalOutputStream.write(body);
    }

    /**
     * Materializes the current response headers into a flat map understood by the signature module.
     *
     * @param context writer interceptor context
     * @return single-valued response headers
     */
    private Map<String, String> responseHeaders(WriterInterceptorContext context) {
        Map<String, String> headers = new LinkedHashMap<>();
        context.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty() && values.get(0) != null) {
                headers.put(name, String.valueOf(values.get(0)));
            }
        });
        return headers;
    }

    /**
     * Ensures late transport headers are visible before the signature is computed.
     *
     * @param context writer interceptor context
     * @param body final serialized response body
     */
    private void ensureTransportHeaders(WriterInterceptorContext context, byte[] body) {
        if (context.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE) == null && context.getMediaType() != null) {
            context.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, context.getMediaType().toString());
        }
        if (context.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH) == null) {
            context.getHeaders().putSingle(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        }
    }

    /**
     * Adds response-local headers that must exist before the signature is computed.
     *
     * @param context writer interceptor context
     * @param headers mutable response header map used for signature generation
     * @param policy resolved inbound response-generation policy
     */
    private void ensureGeneratedResponseHeaders(
            WriterInterceptorContext context,
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
            if (context.getHeaders().getFirst(SignatureConstants.HTTP_HEADER_RESPONSE_TIMESTAMP) == null) {
                context.getHeaders().putSingle(SignatureConstants.HTTP_HEADER_RESPONSE_TIMESTAMP, responseTimestamp);
            }
        }
    }

    /**
     * Builds the detached request view used by response components declared with {@code ;req}.
     *
     * @param request current Vert.x HTTP server request
     * @return request view exposing the request headers and target metadata
     */
    private OutgoingHttpMessage requestMessage(HttpServerRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.headers().forEach(entry -> {
            if (entry.getKey() != null && entry.getValue() != null) {
                headers.put(entry.getKey(), entry.getValue());
            }
        });

        return new OutgoingHttpMessage(
                request.method().name(),
                URI.create(request.absoluteURI()),
                headers,
                new byte[0],
                null
        );
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
