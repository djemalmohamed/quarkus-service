package com.example.legalarchive.infrastructure.adapters.out.http;

import com.example.legalarchive.application.LegalArchivingUseCase;
import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.application.policy.ArchiveDecisionContext;
import com.example.legalarchive.application.policy.LegalArchivingPolicy;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbound Vert.x HTTP client wrapper that archives request and response exchanges through Kafka.
 */
@ApplicationScoped
@Slf4j
public class VertxReactiveLegalArchivingClient {

    private static final String REQUEST_ID_HEADER = "Request-Id";

    private final WebClient webClient;
    private final LegalArchivingPolicy legalArchivingPolicy;
    private final VertxReactiveLegalArchivingEventFactory eventFactory;
    private final LegalArchivingUseCase legalArchivingUseCase;

    public VertxReactiveLegalArchivingClient(
            Vertx vertx,
            LegalArchivingPolicy legalArchivingPolicy,
            VertxReactiveLegalArchivingEventFactory eventFactory,
            LegalArchivingUseCase legalArchivingUseCase) {
        this(
                WebClient.create(Objects.requireNonNull(vertx, "vertx must not be null")),
                legalArchivingPolicy,
                eventFactory,
                legalArchivingUseCase);
    }

    VertxReactiveLegalArchivingClient(
            WebClient webClient,
            LegalArchivingPolicy legalArchivingPolicy,
            VertxReactiveLegalArchivingEventFactory eventFactory,
            LegalArchivingUseCase legalArchivingUseCase) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.legalArchivingPolicy = legalArchivingPolicy;
        this.eventFactory = eventFactory;
        this.legalArchivingUseCase = legalArchivingUseCase;
    }

    /**
     * Executes an outbound HTTP request through Vert.x and archives the request/response exchange when enabled.
     *
     * @param request the outbound request to execute
     * @return a Vert.x future resolving to the HTTP response
     */
    public Future<HttpResponse<Buffer>> send(VertxReactiveHttpRequest request) {
        ArchiveDecisionContext decisionContext = toDecisionContext(request);
        boolean archiveEnabled = legalArchivingPolicy.shouldArchive(decisionContext);
        VertxReactiveHttpRequest effectiveRequest = archiveEnabled ? ensureRequestId(request) : request;
        String requestId = archiveEnabled ? effectiveRequest.headers().get(REQUEST_ID_HEADER) : null;
        String operation = archiveEnabled
                ? resolveOperation(effectiveRequest, decisionContext)
                : null;

        if (archiveEnabled) {
            archiveAsync(
                    eventFactory.outboundRequest(requestId, operation, effectiveRequest),
                    requestId,
                    operation,
                    "REQUEST");
        }

        HttpRequest<Buffer> httpRequest =
                webClient.requestAbs(effectiveRequest.method(), effectiveRequest.uri().toString());
        httpRequest.putHeaders(effectiveRequest.headers());

        Future<HttpResponse<Buffer>> responseFuture = effectiveRequest.hasBody()
                ? httpRequest.sendBuffer(effectiveRequest.body())
                : httpRequest.send();

        if (!archiveEnabled) {
            return responseFuture;
        }

        return responseFuture
                .onSuccess(response -> archiveAsync(
                        eventFactory.outboundResponse(requestId, operation, effectiveRequest, response),
                        requestId,
                        operation,
                        "RESPONSE"))
                .onFailure(error -> log.warn(
                        "Outbound HTTP exchange failed before a response could be archived requestId={} operation={}",
                        requestId,
                        operation,
                        error));
    }

    @PreDestroy
    void close() {
        webClient.close();
    }

    /**
     * Dispatches the legal-archiving use case asynchronously without blocking the HTTP call flow.
     *
     * @param event the event to archive
     * @param requestId the current request identifier
     * @param operation the derived business operation
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
                                "Outbound legal archiving failed requestId={} operation={} phase={}",
                                requestId,
                                operation,
                                phase,
                                error));
    }

    /**
     * Ensures that the outbound request carries a {@code Request-Id} header before leaving the service.
     *
     * @param request the outbound request to enrich when needed
     * @return the original request when it already contains a request identifier, otherwise a copied enriched request
     */
    private VertxReactiveHttpRequest ensureRequestId(VertxReactiveHttpRequest request) {
        String requestId = request.headers().get(REQUEST_ID_HEADER);
        if (null != requestId && !requestId.isBlank()) {
            return request;
        }

        var headers = request.headers();
        headers.set(REQUEST_ID_HEADER, UUID.randomUUID().toString());
        return new VertxReactiveHttpRequest(
                request.method(),
                request.uri(),
                headers,
                request.body(),
                request.operation(),
                request.signatureData());
    }

    /**
     * Resolves the operation name from the explicit override or from the application legal-archiving policy.
     *
     * @param request the outbound request to describe
     * @param decisionContext the neutral outbound context derived from the request
     * @return the business operation name attached to the archived event
     */
    private String resolveOperation(VertxReactiveHttpRequest request, ArchiveDecisionContext decisionContext) {
        String operation = request.operation();
        if (null != operation && !operation.isBlank()) {
            return operation;
        }
        return legalArchivingPolicy.resolveOperation(decisionContext);
    }

    /**
     * Converts the Vert.x request metadata to the neutral application decision context.
     *
     * @param request the outbound request
     * @return the application decision context used by the legal-archiving policy
     */
    private ArchiveDecisionContext toDecisionContext(VertxReactiveHttpRequest request) {
        return ArchiveDecisionContext.outbound(
                request.method().name(),
                resolveHost(request),
                request.uri().getPath());
    }

    /**
     * Resolves the outbound host while keeping the previous authority fallback for non-standard URIs.
     *
     * @param request the outbound request
     * @return the host or authority used by the application policy
     */
    private String resolveHost(VertxReactiveHttpRequest request) {
        String host = request.uri().getHost();
        if (null != host && !host.isBlank()) {
            return host;
        }
        String authority = request.uri().getAuthority();
        return null == authority ? "" : authority;
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
