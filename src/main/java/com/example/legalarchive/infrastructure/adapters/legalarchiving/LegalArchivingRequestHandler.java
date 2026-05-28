package com.example.legalarchive.infrastructure.adapters.legalarchiving;

import com.example.legalarchive.application.LegalArchivingUseCase;
import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.application.policy.ArchiveDecisionContext;
import com.example.legalarchive.application.policy.LegalArchivingPolicy;
import com.example.legalarchive.infrastructure.adapters.in.rest.ArchivePayloadSerializer;
import com.example.legalarchive.infrastructure.signature.HttpMessageSignatureContextKeys;
import com.example.legalarchive.infrastructure.signature.model.SignatureData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles inbound request archiving once the upstream signature flow has already prepared the
 * request body and signature data.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LegalArchivingRequestHandler {

    private static final String REQUEST_ID_HEADER = "Request-Id";

    private final LegalArchivingPolicy legalArchivingPolicy;
    private final ArchivePayloadSerializer payloadSerializer;
    private final LegalArchivingEventMapper eventMapper;
    private final LegalArchivingUseCase legalArchivingUseCase;

    /**
     * Archives one inbound HTTP request when the legal-archiving policy enables it.
     *
     * <p>The caller is expected to belong to the signature flow and therefore to have already:
     * prepared the request body bytes, populated the request signature data property, and reset
     * the entity stream for downstream processing.
     *
     * @param requestContext the current inbound request context
     * @param requestBody the already materialized request body bytes, or {@code null} when absent
     */
    public void handle(ContainerRequestContext requestContext, byte[] requestBody) {
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        ArchiveDecisionContext decisionContext = ArchiveDecisionContext.inbound(requestContext.getMethod(), path);
        if (!legalArchivingPolicy.shouldArchive(decisionContext)) {
            return;
        }

        String requestId = resolveRequestId(requestContext);
        String operation = legalArchivingPolicy.resolveOperation(decisionContext);
        requestContext.setProperty(LegalArchivingContextKeys.ARCHIVE_ENABLED, Boolean.TRUE);
        requestContext.setProperty(LegalArchivingContextKeys.REQUEST_ID, requestId);
        requestContext.setProperty(LegalArchivingContextKeys.OPERATION, operation);

        archiveAsync(
                eventMapper.toEvent(
                        requestId,
                        operation,
                        "INBOUND",
                        "REQUEST",
                        payloadSerializer.serialize(requestBody),
                        (SignatureData) requestContext.getProperty(
                                HttpMessageSignatureContextKeys.REQUEST_SIGNATURE_DATA)),
                requestId,
                operation);
    }

    /**
     * Triggers legal archiving asynchronously so the caller can keep its HTTP pipeline non-blocking.
     *
     * @param event the event ready for the legal-archiving use case
     * @param requestId the current request identifier
     * @param operation the logical operation derived from the request
     */
    private void archiveAsync(
            LegalArchivingEvent event,
            String requestId,
            String operation) {
        legalArchivingUseCase.archive(event)
                .subscribe()
                .with(
                        this::ignoreArchiveSuccess,
                        error -> log.error(
                                "Inbound legal archiving failed requestId={} operation={} phase={}",
                                requestId,
                                operation,
                                "REQUEST",
                                error));
    }

    /**
     * Resolves the request identifier from the incoming HTTP headers, generating one when absent.
     *
     * @param requestContext the current inbound request context
     * @return the propagated or generated request identifier
     */
    private String resolveRequestId(ContainerRequestContext requestContext) {
        String requestId = requestContext.getHeaderString(REQUEST_ID_HEADER);
        if (null != requestId && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
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
