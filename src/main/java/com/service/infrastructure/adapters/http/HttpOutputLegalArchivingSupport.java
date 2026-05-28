package com.service.infrastructure.adapters.http;

import com.service.application.port.in.LegalArchivingInPort;
import com.service.application.legalarchiving.policy.ArchiveDecisionContext;
import com.service.application.legalarchiving.policy.LegalArchivingPolicy;
import com.service.infrastructure.adapters.legalarchiving.LegalArchivingEventMapper;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.validation.model.SignatureData;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbound legal-archiving helper dedicated to the custom HTTP output adapter used by the gateway.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class HttpOutputLegalArchivingSupport {

    private final LegalArchivingPolicy legalArchivingPolicy;
    private final LegalArchivingEventMapper legalArchivingEventMapper;
    private final LegalArchivingInPort legalArchivingInPort;

    /**
     * Archives one outbound request when the configured legal-archiving policy applies.
     *
     * @param method outbound HTTP method
     * @param uri outbound target URI
     * @param headers final outbound request headers
     * @param body outbound request body
     * @param signatureData outbound signature data prepared by the signing layer
     */
    public void archiveRequest(
            String method,
            URI uri,
            Map<String, String> headers,
            String body,
            SignatureData signatureData) {
        archive(method, uri, headers, body, signatureData, "REQUEST");
    }

    /**
     * Archives one outbound response when the configured legal-archiving policy applies.
     *
     * @param method original outbound HTTP method
     * @param uri outbound target URI
     * @param headers original outbound request headers used to recover the request identifier
     * @param body response body
     * @param signatureData response signature data prepared by validation when available
     */
    public void archiveResponse(
            String method,
            URI uri,
            Map<String, String> headers,
            String body,
            SignatureData signatureData) {
        archive(method, uri, headers, body, signatureData, "RESPONSE");
    }

    /**
     * Performs the common outbound policy evaluation and asynchronous legal-archiving publication.
     *
     * @param method outbound HTTP method
     * @param uri outbound target URI
     * @param headers request headers carrying the business correlation identifier
     * @param body request or response body
     * @param signatureData signature data to archive when available
     * @param phase request or response phase
     */
    private void archive(
            String method,
            URI uri,
            Map<String, String> headers,
            String body,
            SignatureData signatureData,
            String phase) {
        ArchiveDecisionContext context = ArchiveDecisionContext.outbound(method, uri.getHost(), uri.getRawPath());
        if (!legalArchivingPolicy.shouldArchive(context)) {
            return;
        }

        String eventId = resolveRequestId(headers);
        String operation = legalArchivingPolicy.resolveOperation(context);
        byte[] payload = body == null || body.isEmpty() ? null : body.getBytes(StandardCharsets.UTF_8);

        legalArchivingInPort.archive(legalArchivingEventMapper.toEvent(
                        eventId,
                        operation,
                        "OUTBOUND",
                        phase,
                        payload,
                        signatureData))
                .subscribe()
                .with(
                        this::ignoreArchiveSuccess,
                        error -> log.error(
                                "Outbound legal archiving failed eventId={} operation={} phase={}",
                                eventId,
                                operation,
                                phase,
                                error));
    }

    /**
     * Resolves the event identifier from outbound headers, generating one when missing.
     *
     * @param headers outbound request headers
     * @return propagated or generated event identifier
     */
    private String resolveRequestId(Map<String, String> headers) {
        if (null != headers) {
            String eventId = firstHeaderValue(headers, SignatureConstants.HEADER_REQUEST_ID);
            if (null != eventId && !eventId.isBlank()) {
                return eventId;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Finds one header value through case-insensitive lookup.
     *
     * @param headers outbound headers
     * @param headerName logical header name to look for
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
     * Intentionally ignores successful asynchronous publications because only failures need logs.
     *
     * @param ignored successful completion signal
     */
    private void ignoreArchiveSuccess(Void ignored) {
        // No-op by design.
    }
}
