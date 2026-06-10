package com.service.infrastructure.adapters.legalarchiving;

import com.service.application.port.in.LegalArchivingInPort;
import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.infrastructure.adapters.legalarchiving.LegalArchivingContextKeys;
import com.service.infrastructure.adapters.legalarchiving.LegalArchivingEventMapper;
import com.service.infrastructure.signature.SignatureContextKeys;
import com.service.infrastructure.signature.validation.model.SignatureData;
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

    private final ObjectSerializer payloadSerializer;
    private final LegalArchivingEventMapper eventMapper;
    private final LegalArchivingInPort legalArchivingInPort;

    @ServerResponseFilter(priority = LEGAL_ARCHIVING_PRIORITY)
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!Boolean.TRUE.equals(requestContext.getProperty(LegalArchivingContextKeys.ARCHIVE_ENABLED))) {
            return;
        }

        String operationId = (String) requestContext.getProperty(LegalArchivingContextKeys.OPERATION_ID);
        String operation = (String) requestContext.getProperty(LegalArchivingContextKeys.OPERATION);
        if (null != operationId && null == responseContext.getHeaders().getFirst(REQUEST_ID_HEADER)) {
            responseContext.getHeaders().putSingle(REQUEST_ID_HEADER, operationId);
        }
        archiveAsync(
                eventMapper.toEvent(
                        operationId,
                        operation,
                        "INBOUND",
                        "RESPONSE",
                        new LegalArchivingEvent.HttpContext(
                                requestContext.getMethod(),
                                requestContext.getUriInfo().getRequestUri().getPath()),
                        payloadSerializer.serialize(responseContext.getEntity()),
                        (SignatureData) requestContext.getProperty(
                                SignatureContextKeys.RESPONSE_SIGNATURE_DATA)),
                operationId,
                operation,
                "RESPONSE");
    }

    /**
     * Triggers legal archiving asynchronously so the HTTP pipeline can continue without waiting for Kafka.
     *
     * @param event the legal-archiving event to publish
     * @param operationId the current operation identifier
     * @param operation the business operation derived from the request
     * @param phase the archived interaction phase
     */
    private void archiveAsync(
            LegalArchivingEvent event,
            String operationId,
            String operation,
            String phase) {
        legalArchivingInPort.archive(event)
                .subscribe()
                .with(
                        this::ignoreArchiveSuccess,
                        error -> log.error(
                                "Inbound legal archiving failed operationId={} operation={} phase={}",
                                operationId,
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
