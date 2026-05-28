package com.playground.gateway.infrastructure.signature.validation.rest;

import com.example.legalarchive.infrastructure.adapters.legalarchiving.LegalArchivingRequestHandler;
import com.example.legalarchive.infrastructure.signature.HttpMessageSignatureContextKeys;
import com.example.legalarchive.infrastructure.signature.SignatureDataBridge;
import com.playground.gateway.infrastructure.signature.shared.SignatureConstants;
import com.playground.gateway.infrastructure.signature.shared.service.SignaturePolicyResolver;
import com.playground.gateway.infrastructure.signature.validation.error.SignatureValidationException;
import com.playground.gateway.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.playground.gateway.infrastructure.signature.validation.model.ValidationReport;
import com.playground.gateway.infrastructure.signature.validation.service.SignatureValidationEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inbound HTTP filter responsible for signature validation before the REST adapter is invoked.
 *
 * <p>The filter converts the transport request into the internal signature model, resolves the
 * matching endpoint rule and delegates the actual validation to the signature engine. Internal
 * Quarkus management endpoints are intentionally ignored so the gateway configuration does not
 * interfere with platform tooling.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SignatureValidationFilter {

    private final SignaturePolicyResolver policyResolver;

    private final SignatureValidationEngine signatureValidationEngine;

    private final SignatureDataBridge signatureDataBridge;

    private final LegalArchivingRequestHandler legalArchivingRequestHandler;

    /**
     * Intercepts inbound REST requests and delegates signature validation before resource handling.
     *
     * @param requestContext inbound JAX-RS request context
     * @return optional early HTTP response when the framework requires one, otherwise {@code null}
     * @throws IOException when the request body cannot be materialized for validation
     */
    @ServerRequestFilter(priority = Priorities.AUTHENTICATION, readBody = true)
    public Response filter(ContainerRequestContext requestContext) throws IOException {
        if (isInternalPath(requestContext.getUriInfo().getRequestUri().getPath())) {
            return null;
        }

        Map<String, List<String>> headers = toHeaders(requestContext);
        IncomingHttpMessage previewMessage = new IncomingHttpMessage(
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri().getRawPath(),
                requestContext.getUriInfo().getRequestUri().getRawQuery(),
                authority(requestContext.getUriInfo().getRequestUri()),
                headers,
                new byte[0],
                null
        );

        var policy = policyResolver.resolveInboundValidationOptional(previewMessage);
        if (policy.isEmpty()) {
            return null;
        }

        byte[] body = policy.get().bodyRequired() ? requestContext.getEntityStream().readAllBytes() : new byte[0];
        if (policy.get().bodyRequired()) {
            requestContext.setEntityStream(new ByteArrayInputStream(body));
        }

        IncomingHttpMessage message = new IncomingHttpMessage(
                previewMessage.method(),
                previewMessage.path(),
                previewMessage.query(),
                previewMessage.authority(),
                headers,
                body,
                null
        );

        ValidationReport report = signatureValidationEngine.validate(message, policy.get());
        requestContext.setProperty(SignatureValidationRequestContextKeys.VALIDATION_REPORT, report);
        if (null != report.signatureData()) {
            requestContext.setProperty(
                    HttpMessageSignatureContextKeys.REQUEST_SIGNATURE_DATA,
                    signatureDataBridge.toLegalArchivingSignatureData(report.signatureData()));
        }
        legalArchivingRequestHandler.handle(requestContext, body.length == 0 ? null : body);
        if (policy.get().rejectOnFailure() && !report.valid()) {
            throw new SignatureValidationException(report);
        }
        return null;
    }

    /**
     * Materializes the JAX-RS multivalued header structure into an ordered map understood by the engine.
     *
     * @param requestContext inbound request context
     * @return ordered header map preserving the original header names
     */
    private Map<String, List<String>> toHeaders(ContainerRequestContext requestContext) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        requestContext.getHeaders().forEach((name, values) -> {
            String normalizedName = name == null ? "" : name.trim().toLowerCase();
            if (normalizedName.isBlank()) {
                return;
            }

            List<String> bucket = headers.computeIfAbsent(normalizedName, ignored -> new ArrayList<>());
            bucket.addAll(values);
        });

        Map<String, List<String>> immutableHeaders = new LinkedHashMap<>();
        headers.forEach((name, values) -> immutableHeaders.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(immutableHeaders);
    }

    /**
     * Extracts the authority portion of the current request URI.
     *
     * @param uri request URI
     * @return authority component or an empty string when unavailable
     */
    private String authority(URI uri) {
        return uri == null ? "" : uri.getAuthority();
    }

    /**
     * Identifies Quarkus internal endpoints that should not be intercepted by business security rules.
     *
     * @param path request path
     * @return {@code true} when the path belongs to Quarkus internal tooling
     */
    private boolean isInternalPath(String path) {
        return path != null && path.startsWith(SignatureConstants.INTERNAL_QUARKUS_PATH_PREFIX);
    }
}
