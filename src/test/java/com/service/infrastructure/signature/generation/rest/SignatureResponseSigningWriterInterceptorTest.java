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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignatureResponseSigningWriterInterceptorTest {

    @Mock
    private SignatureGenerationService signatureGenerationService;

    @Mock
    private SignaturePolicyResolver policyResolver;

    @Mock
    private CurrentVertxRequest currentVertxRequest;

    @Mock
    private WriterInterceptorContext context;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest request;

    @Mock
    private HttpServerResponse response;

    private SignatureResponseSigningWriterInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SignatureResponseSigningWriterInterceptor(
                signatureGenerationService,
                policyResolver,
                currentVertxRequest
        );
    }

    @Test
    void shouldWriteBodyAndSkipInternalPath() throws Exception {
        ByteArrayOutputStream original = prepareContext("{\"status\":\"ok\"}");
        when(currentVertxRequest.getCurrent()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(request.path()).thenReturn("/q/health");

        interceptor.aroundWriteTo(context);

        assertEquals("{\"status\":\"ok\"}", original.toString(StandardCharsets.UTF_8));
        verify(policyResolver, never()).resolveInboundGeneration(any(), any(OutgoingHttpMessage.class));
    }

    @Test
    void shouldSignSerializedResponseWhenPolicyApplies() throws Exception {
        ByteArrayOutputStream original = prepareContext("{\"status\":\"ok\"}");
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        ResolvedGenerationPolicy policy = responsePolicy();
        MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap().add("Request-ID", "req-1");

        when(context.getHeaders()).thenReturn(headers);
        when(currentVertxRequest.getCurrent()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(request.path()).thenReturn("/v1/payments");
        when(request.absoluteURI()).thenReturn("https://gateway.example.com/v1/payments");
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.headers()).thenReturn(requestHeaders);
        when(response.getStatusCode()).thenReturn(201);
        when(policyResolver.resolveInboundGeneration(eq(null), any(OutgoingHttpMessage.class))).thenReturn(Optional.of(policy));
        when(signatureGenerationService.sign(any(SignatureContextMessage.class), eq(policy))).thenReturn(
                new SignatureGenerationResult(
                        "response-policy",
                        Map.of("Signature", "sig=:AQID:", "Signature-Input", "sig=(\"@status\")")
                )
        );

        interceptor.aroundWriteTo(context);

        assertEquals("{\"status\":\"ok\"}", original.toString(StandardCharsets.UTF_8));
        assertEquals("application/json", headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals(String.valueOf("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8).length), headers.getFirst(HttpHeaders.CONTENT_LENGTH));
        assertEquals("sig=:AQID:", headers.getFirst("Signature"));
        assertEquals("sig=(\"@status\")", headers.getFirst("Signature-Input"));
        assertNotNull(headers.getFirst("Response-Timestamp"));
    }

    @Test
    void shouldKeepResponseUnsignedWhenSigningCannotResolveAllComponents() throws Exception {
        ByteArrayOutputStream original = prepareContext("{\"status\":\"ok\"}");
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        ResolvedGenerationPolicy policy = responsePolicy();
        MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap().add("Request-ID", "req-1");

        when(context.getHeaders()).thenReturn(headers);
        when(currentVertxRequest.getCurrent()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(request.path()).thenReturn("/v1/payments");
        when(request.absoluteURI()).thenReturn("https://gateway.example.com/v1/payments");
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.headers()).thenReturn(requestHeaders);
        when(response.getStatusCode()).thenReturn(201);
        when(policyResolver.resolveInboundGeneration(eq(null), any(OutgoingHttpMessage.class))).thenReturn(Optional.of(policy));
        when(signatureGenerationService.sign(any(SignatureContextMessage.class), eq(policy)))
                .thenThrow(new MissingCoveredComponentException("@status"));

        interceptor.aroundWriteTo(context);

        assertEquals("{\"status\":\"ok\"}", original.toString(StandardCharsets.UTF_8));
        assertEquals(null, headers.getFirst("Signature"));
        verify(signatureGenerationService).sign(any(SignatureContextMessage.class), eq(policy));
    }

    private ByteArrayOutputStream prepareContext(String body) throws Exception {
        ByteArrayOutputStream originalOutput = new ByteArrayOutputStream();
        AtomicReference<OutputStream> currentOutput = new AtomicReference<>(originalOutput);
        when(context.getOutputStream()).thenReturn(originalOutput);
        when(context.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        when(context.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        doAnswer(invocation -> {
            currentOutput.set(invocation.getArgument(0));
            return null;
        }).when(context).setOutputStream(any(OutputStream.class));
        doAnswer(invocation -> {
            currentOutput.get().write(body.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(context).proceed();
        return originalOutput;
    }

    private ResolvedGenerationPolicy responsePolicy() {
        return new ResolvedGenerationPolicy(
                "response-policy",
                "sig",
                "gateway-key",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                List.of(
                        CoveredComponent.of(SignatureConstants.HEADER_RESPONSE_TIMESTAMP),
                        CoveredComponent.of(SignatureConstants.COMPONENT_STATUS),
                        CoveredComponent.request(SignatureConstants.HEADER_REQUEST_ID)
                ),
                false,
                true,
                false,
                Duration.ofMinutes(5),
                false,
                0
        );
    }
}
