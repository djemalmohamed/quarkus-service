package com.example.legalarchive.infrastructure.adapters.out.http;

import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.infrastructure.adapters.legalarchiving.LegalArchivingEventMapper;
import com.example.legalarchive.infrastructure.adapters.in.rest.ArchivePayloadSerializer;
import com.example.legalarchive.infrastructure.signature.model.SignatureData;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * Builds domain legal-archiving events from outbound Vert.x HTTP exchanges.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class VertxReactiveLegalArchivingEventFactory {

    private final ArchivePayloadSerializer payloadSerializer;
    private final LegalArchivingEventMapper eventMapper;

    /**
     * Creates the legal-archiving event associated with an outbound HTTP request.
     *
     * @param requestId the correlation identifier of the archived HTTP interaction
     * @param operation the logical business operation name
     * @param request the outbound request metadata
     * @return a domain event aligned with the legal-archiving use case
     */
    public LegalArchivingEvent outboundRequest(
            String requestId,
            String operation,
            VertxReactiveHttpRequest request) {
        return buildEvent(
                requestId,
                operation,
                "REQUEST",
                request.body(),
                request.signatureData());
    }

    /**
     * Creates the legal-archiving event associated with an outbound HTTP response.
     *
     * @param requestId the correlation identifier of the archived HTTP interaction
     * @param operation the logical business operation name
     * @param request the original outbound request metadata
     * @param response the outbound HTTP response returned by Vert.x
     * @return a domain event aligned with the legal-archiving use case
     */
    public LegalArchivingEvent outboundResponse(
            String requestId,
            String operation,
            VertxReactiveHttpRequest request,
            HttpResponse<Buffer> response) {
        return buildEvent(
                requestId,
                operation,
                "RESPONSE",
                null == response ? Buffer.buffer() : response.bodyAsBuffer(),
                null);
    }

    /**
     * Builds the domain event shared by outbound request and response archiving.
     *
     * @param requestId the request identifier propagated through the outbound interaction
     * @param operation the derived business operation
     * @param phase whether the archived event represents the request or the response
     * @param body the outbound payload to archive when present
     * @param signatureData the signature data already prepared by the signing flow
     * @return the legal-archiving domain event assembled from outbound HTTP metadata
     */
    private LegalArchivingEvent buildEvent(
            String requestId,
            String operation,
            String phase,
            Buffer body,
            SignatureData signatureData) {
        byte[] payload = null == body || body.length() == 0
                ? null
                : payloadSerializer.serialize(body.getBytes());

        return eventMapper.toEvent(
                requestId,
                operation,
                "OUTBOUND",
                phase,
                payload,
                signatureData);
    }
}
