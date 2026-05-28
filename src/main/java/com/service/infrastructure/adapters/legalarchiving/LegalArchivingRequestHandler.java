package com.service.infrastructure.adapters.legalarchiving;

import com.service.application.legalarchiving.LegalArchivingUseCase;
import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.legalarchiving.policy.ArchiveDecisionContext;
import com.service.application.legalarchiving.policy.LegalArchivingPolicy;
import com.service.infrastructure.adapters.legalarchiving.ArchivePayloadSerializer;
import com.service.infrastructure.signature.SignatureContextKeys;
import com.service.infrastructure.signature.validation.model.SignatureData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles inbound request archiving once the upstream signature flow has already prepared the
 * request body and signature data.
 *
 * <p>This component exists so request-side legal archiving is triggered explicitly from the
 * signature pipeline instead of relying on a second request filter. In this service, the request
 * signature flow is the technical place that already owns:
 * the materialized request body, the resolved {@link SignatureData}, and the moment where the
 * entity stream has been safely reset for the rest of the HTTP chain.</p>
 *
 * <p>Keeping request archiving here avoids fragile ordering assumptions between multiple request
 * filters. The signature filter can finish its own work first, then delegate to this handler in a
 * deterministic way. The legal-archiving filter is therefore kept for response-side concerns,
 * while request-side orchestration happens immediately after signature preparation.</p>
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
     * <p>This design intentionally keeps the request-side legal-archiving trigger in sequence
     * after signature preparation. The signature layer is the only place that reliably knows when
     * the body has been read, when the covered-component values have been resolved, and when the
     * final {@code Signature} and {@code Signature-Input} header values are available for reuse by
     * legal archiving.</p>
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
                                SignatureContextKeys.REQUEST_SIGNATURE_DATA)),
                requestId,
                operation);
    }

    /**
     * Triggers legal archiving asynchronously so the caller can keep its HTTP pipeline non-blocking.
     *
     * <p>The request has already been fully prepared at this stage, so the handler only needs to
     * delegate to the use case and log failures without slowing down the inbound request chain.</p>
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
