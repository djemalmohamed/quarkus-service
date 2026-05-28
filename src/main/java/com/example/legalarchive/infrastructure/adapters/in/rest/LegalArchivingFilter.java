package com.example.legalarchive.infrastructure.adapters.in.rest;

import com.example.legalarchive.application.LegalArchivingUseCase;
import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.infrastructure.adapters.legalarchiving.LegalArchivingContextKeys;
import com.example.legalarchive.infrastructure.adapters.legalarchiving.LegalArchivingEventMapper;
import com.example.legalarchive.infrastructure.signature.HttpMessageSignatureContextKeys;
import com.example.legalarchive.infrastructure.signature.model.SignatureData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Inbound HTTP response adapter that finalizes legal archiving after the request side has already
 * been orchestrated by the upstream signature flow.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LegalArchivingFilter {

    private static final String REQUEST_ID_HEADER = "Request-Id";
    private static final int LEGAL_ARCHIVING_PRIORITY = 5020;

    private final ArchivePayloadSerializer payloadSerializer;
    private final LegalArchivingEventMapper eventMapper;
    private final LegalArchivingUseCase legalArchivingUseCase;

    @ServerResponseFilter(priority = LEGAL_ARCHIVING_PRIORITY)
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!Boolean.TRUE.equals(requestContext.getProperty(LegalArchivingContextKeys.ARCHIVE_ENABLED))) {
            return;
        }

        String requestId = (String) requestContext.getProperty(LegalArchivingContextKeys.REQUEST_ID);
        String operation = (String) requestContext.getProperty(LegalArchivingContextKeys.OPERATION);
        if (null != requestId && null == responseContext.getHeaders().getFirst(REQUEST_ID_HEADER)) {
            responseContext.getHeaders().putSingle(REQUEST_ID_HEADER, requestId);
        }
        archiveAsync(
                eventMapper.toEvent(
                        requestId,
                        operation,
                        "INBOUND",
                        "RESPONSE",
                        payloadSerializer.serialize(responseContext.getEntity()),
                        (SignatureData) requestContext.getProperty(
                                HttpMessageSignatureContextKeys.RESPONSE_SIGNATURE_DATA)),
                requestId,
                operation,
                "RESPONSE");
    }

    /**
     * Triggers legal archiving asynchronously so the HTTP pipeline can continue without waiting for Kafka.
     *
     * @param event the legal-archiving event to publish
     * @param requestId the current request identifier
     * @param operation the business operation derived from the request
     * @param phase the archived interaction phase
     */
    private void archiveAsync(
            LegalArchivingEvent event,
            String requestId,
            String operation,
            String phase) {
        legalArchivingUseCase.archive(event)
                .subscribe()
                .with(
                        this::ignoreArchiveSuccess,
                        error -> log.error(
                                "Inbound legal archiving failed requestId={} operation={} phase={}",
                                requestId,
                                operation,
                                phase,
                                error));
    }

    /**
     * Intentionally ignores successful asynchronous archiving callbacks because only failures need explicit logs.
     *
     * @param ignored the successful {@link Void} result emitted by the asynchronous archive call
     */
    private void ignoreArchiveSuccess(Void ignored) {
        // No-op by design.
    }
}
