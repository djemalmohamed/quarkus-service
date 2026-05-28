package com.service.infrastructure.adapters.http;

import com.service.infrastructure.adapters.http.model.RequestPayload;
import com.service.infrastructure.adapters.http.model.ResponsePayload;
import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.generation.service.SignatureGenerationService;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.service.infrastructure.signature.validation.model.ValidationIssue;
import com.service.infrastructure.signature.validation.model.ValidationReport;
import com.service.infrastructure.signature.validation.service.SignatureValidationEngine;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal reactive outbound HTTP adapter built around Mutiny.
 *
 * <p>This component is intentionally small so it can simulate a custom in-house reactive
 * HTTP client. Request signing and synchronous response validation are both orchestrated here
 * so the adapter remains the single transport entry point.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HttpOutputAdapter {

    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_DELETE = "DELETE";
    private static final String DEFAULT_HTTP_FAILURE_MESSAGE = "HTTP call failed";
    private static final Logger LOG = Logger.getLogger(HttpOutputAdapter.class);

    private HttpClient httpClient;

    private final GatewayConfig config;

    private final SignatureGenerationService signatureGenerationService;

    private final SignatureValidationEngine signatureValidationEngine;

    private final SignaturePolicyResolver signaturePolicyResolver;

    private final HttpOutputLegalArchivingSupport legalArchivingSupport;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(config.outbound().connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Executes an outbound HTTP request reactively.
     *
     * @param payload caller-provided request definition that may reference a configured target
     * @return asynchronous technical response enriched with signature metadata
     */
    public Uni<ResponsePayload> execute(RequestPayload payload) {
        if (payload == null) {
            throw new SignatureConfigurationException("Request payload is required");
        }

        String method = resolveMethod(payload);
        URI uri = resolveUri(payload);
        String body = payload.getBody();
        String requestSignatureEndpoint = resolveRequestSignatureEndpoint(payload);
        Map<String, String> requestHeaders = resolveHeaders(payload);
        ensureRequestIdHeader(requestHeaders);
        Instant start = Instant.now();

        SignatureGenerationResult requestSignature = signatureGenerationService.sign(
                new OutgoingHttpMessage(
                        method,
                        uri,
                        new LinkedHashMap<>(requestHeaders),
                        bodyBytes(body),
                        null
                ),
                payload.getTarget(),
                requestSignatureEndpoint
        );
        requestHeaders.putAll(requestSignature.headers());
        legalArchivingSupport.archiveRequest(method, uri, requestHeaders, body, requestSignature.signatureData());

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(config.outbound().readTimeout());
        requestHeaders.forEach(requestBuilder::header);
        requestBuilder.method(
                method,
                bodyPublisher(method, body)
        );

        return Uni.createFrom()
                .completionStage(httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()))
                .onItem().transform(response -> validateResponseSignature(
                        method,
                        uri,
                        body,
                        requestHeaders,
                        response
                ))
                .onItem().transform(validatedResponse -> toResponsePayload(
                        start,
                        requestSignature,
                        validatedResponse
                ))
                .onFailure().recoverWithItem(error -> errorResponse(start, throwableMessage(error)));
    }

    /**
     * Maps the validated HTTP response into the internal response payload.
     *
     * @param start exchange start time
     * @param requestSignature generated request signature metadata
     * @param validatedResponse validated HTTP response plus optional signature report
     * @return mapped response payload
     */
    private ResponsePayload toResponsePayload(
            Instant start,
            SignatureGenerationResult requestSignature,
            ValidatedHttpResponse validatedResponse
    ) {
        HttpResponse<String> response = validatedResponse.response();
        ResponsePayload payloadResponse = new ResponsePayload();
        payloadResponse.setStatus(response.statusCode());
        payloadResponse.setDuration(Duration.between(start, Instant.now()).toMillis() + " ms");
        payloadResponse.setBody(response.body());
        payloadResponse.setHeaders(response.headers().map());
        payloadResponse.setRequestSignatureEndpoint(requestSignature.policy());
        payloadResponse.setGeneratedSignatureHeaders(requestSignature.headers());

        ValidationReport responseSignatureReport = validatedResponse.validationReport();
        if (responseSignatureReport != null) {
            payloadResponse.setResponseSignatureEndpoint(responseSignatureReport.policy());
            payloadResponse.getResponseSignatureIssues().addAll(responseSignatureReport.issues());
        }
        return payloadResponse;
    }

    /**
     * Creates an error payload for asynchronous failures.
     *
     * @param start exchange start time
     * @param message failure message
     * @return technical error response
     */
    private ResponsePayload errorResponse(Instant start, String message) {
        ResponsePayload error = new ResponsePayload();
        error.setDuration(Duration.between(start, Instant.now()).toMillis() + " ms");
        error.setError(message);
        return error;
    }

    /**
     * Validates the synchronous HTTP response using the already sent outbound request as context.
     *
     * @param method outbound request method
     * @param uri outbound request URI
     * @param requestBody outbound request body
     * @param requestHeaders final outbound request headers
     * @param response raw HTTP response
     * @return validated response carrying the optional validation report
     */
    private ValidatedHttpResponse validateResponseSignature(
            String method,
            URI uri,
            String requestBody,
            Map<String, String> requestHeaders,
            HttpResponse<String> response
    ) {
        try {
            IncomingHttpMessage responseMessage = new IncomingHttpMessage(
                    method,
                    uri.getRawPath(),
                    uri.getRawQuery(),
                    uri.getAuthority(),
                    response.headers().map(),
                    bodyBytes(response.body()),
                    response.statusCode()
            );
            OutgoingHttpMessage relatedRequest = new OutgoingHttpMessage(
                    method,
                    uri,
                    new LinkedHashMap<>(requestHeaders),
                    bodyBytes(requestBody),
                    null
            );
            SignatureContextMessage context = SignatureContextMessage.withAssociatedRequest(responseMessage, relatedRequest);

            ValidationReport validationReport = signaturePolicyResolver.resolveOutboundValidationOptional(responseMessage)
                    .map(policy -> signatureValidationEngine.validate(context, policy))
                    .orElse(null);
            logResponseSignatureValidation(validationReport);
            legalArchivingSupport.archiveResponse(
                    method,
                    uri,
                    requestHeaders,
                    response.body(),
                    validationReport == null ? null : validationReport.signatureData());
            return new ValidatedHttpResponse(response, validationReport);
        } catch (Exception error) {
            LOG.error("Synchronous response signature validation raised an exception", error);
            legalArchivingSupport.archiveResponse(method, uri, requestHeaders, response.body(), null);
            return new ValidatedHttpResponse(response, null);
        }
    }

    /**
     * Logs the outbound response signature validation outcome without interrupting the HTTP flow.
     *
     * @param validationReport optional response signature validation report
     */
    private void logResponseSignatureValidation(ValidationReport validationReport) {
        if (validationReport == null) {
            return;
        }

        if (!validationReport.issues().isEmpty()) {
            String summary = validationSummary(validationReport);
            LOG.warnf(
                    "Synchronous response signature validation failed for policy=%s, issues=%s",
                    validationReport.policy(),
                    summary
            );
            return;
        }

        LOG.debugf(
                "Synchronous response signature validation passed for policy=%s, keyId=%s, algorithm=%s",
                validationReport.policy(),
                validationReport.keyId(),
                validationReport.algorithm()
        );
    }

    /**
     * Builds one compact validation issue summary suitable for logs and exception messages.
     *
     * @param validationReport response signature validation report
     * @return compact issue summary
     */
    private String validationSummary(ValidationReport validationReport) {
        return validationReport.issues().stream()
                .map(this::formatIssue)
                .reduce((left, right) -> left + ", " + right)
                .orElse("unknown validation issue");
    }

    /**
     * Formats one validation issue for logs.
     *
     * @param issue validation issue
     * @return compact one-line issue representation
     */
    private String formatIssue(ValidationIssue issue) {
        String fieldSuffix = isBlank(issue.field()) ? "" : " [" + issue.field() + "]";
        return issue.code() + fieldSuffix + ": " + issue.message();
    }

    /**
     * Builds the request body publisher matching the outbound method.
     *
     * @param method normalized outbound HTTP method
     * @param body outbound request body
     * @return HTTP body publisher
     */
    private HttpRequest.BodyPublisher bodyPublisher(String method, String body) {
        if (isBodylessMethod(method) && isBlank(body)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body == null ? "" : body);
    }

    /**
     * Determines whether an empty outbound call should omit the request body publisher.
     *
     * @param method normalized HTTP method
     * @return {@code true} when the method is treated as bodyless by the adapter
     */
    private boolean isBodylessMethod(String method) {
        return HTTP_METHOD_GET.equals(method) || HTTP_METHOD_DELETE.equals(method);
    }

    /**
     * Checks whether a textual value is absent.
     *
     * @param value candidate string
     * @return {@code true} when the value is null or blank
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Resolves the final outbound URI from the caller payload and optional target defaults.
     *
     * @param payload caller-provided request payload
     * @return outbound request URI
     */
    private URI resolveUri(RequestPayload payload) {
        String url = payload.getUrl();
        if (!isBlank(payload.getTarget()) && config.targets().containsKey(payload.getTarget()) && isBlank(url)) {
            url = config.targets().get(payload.getTarget()).baseUrl().orElse(null);
        }
        if (isBlank(url)) {
            throw new SignatureConfigurationException("Request URL is required");
        }
        return URI.create(url);
    }

    /**
     * Resolves the outbound HTTP method from the caller payload and optional target defaults.
     *
     * @param payload caller-provided request payload
     * @return uppercase outbound HTTP method
     */
    private String resolveMethod(RequestPayload payload) {
        String method = payload.getMethod();
        if (!isBlank(payload.getTarget()) && config.targets().containsKey(payload.getTarget()) && isBlank(method)) {
            method = config.targets().get(payload.getTarget()).method();
        }
        return method == null ? HTTP_METHOD_GET : method.trim().toUpperCase();
    }

    /**
     * Resolves the request signature endpoint from payload overrides and target defaults.
     *
     * @param payload caller-provided request payload
     * @return request signature endpoint name, or {@code null}
     */
    private String resolveRequestSignatureEndpoint(RequestPayload payload) {
        if (!isBlank(payload.getRequestSignatureEndpoint())) {
            return payload.getRequestSignatureEndpoint();
        }
        if (!isBlank(payload.getTarget()) && config.targets().containsKey(payload.getTarget())) {
            return config.targets().get(payload.getTarget()).requestSignatureEndpoint().orElse(null);
        }
        return null;
    }

    /**
     * Resolves the final outbound headers from target defaults followed by payload overrides.
     *
     * @param payload caller-provided request payload
     * @return mutable header map ready to be enriched with signature headers
     */
    private Map<String, String> resolveHeaders(RequestPayload payload) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!isBlank(payload.getTarget()) && config.targets().containsKey(payload.getTarget())) {
            headers.putAll(config.targets().get(payload.getTarget()).headers());
        }
        if (payload.getHeaders() != null) {
            headers.putAll(payload.getHeaders());
        }
        return headers;
    }

    /**
     * Ensures outbound requests always carry the business correlation identifier expected by both
     * the signature policies and legal-archiving key strategy.
     *
     * @param headers mutable outbound request headers
     */
    private void ensureRequestIdHeader(Map<String, String> headers) {
        if (firstHeaderValue(headers, SignatureConstants.HEADER_REQUEST_ID) != null) {
            return;
        }
        headers.put(SignatureConstants.HTTP_HEADER_REQUEST_ID, java.util.UUID.randomUUID().toString());
    }

    /**
     * Finds one header value through case-insensitive lookup.
     *
     * @param headers outbound headers
     * @param headerName logical header name to resolve
     * @return the matching header value, or {@code null} when absent
     */
    private String firstHeaderValue(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (SignatureConstants.normalize(entry.getKey()).equals(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Encodes a textual payload as UTF-8 bytes.
     *
     * @param body textual payload
     * @return UTF-8 bytes, or an empty array when the body is absent
     */
    private byte[] bodyBytes(String body) {
        return body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Extracts a stable failure message from an exception.
     *
     * @param throwable failure raised by the asynchronous exchange
     * @return best-effort failure message
     */
    private String throwableMessage(Throwable throwable) {
        return throwable == null ? DEFAULT_HTTP_FAILURE_MESSAGE : String.valueOf(throwable.getMessage());
    }

    /**
     * Couples one raw HTTP response with the optional signature validation report produced for it.
     *
     * @param response raw HTTP response
     * @param validationReport optional signature validation report
     */
    private record ValidatedHttpResponse(
            HttpResponse<String> response,
            ValidationReport validationReport
    ) {
    }
}
