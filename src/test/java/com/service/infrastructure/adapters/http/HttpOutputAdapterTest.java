package com.service.infrastructure.adapters.http;

import com.service.infrastructure.adapters.http.model.RequestPayload;
import com.service.infrastructure.adapters.http.model.ResponsePayload;
import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.generation.service.SignatureGenerationService;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import com.service.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.service.infrastructure.signature.validation.model.SignatureData;
import com.service.infrastructure.signature.validation.model.ValidationIssue;
import com.service.infrastructure.signature.validation.model.ValidationReport;
import com.service.infrastructure.signature.validation.service.SignatureValidationEngine;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpOutputAdapterTest {

    @Mock
    private SignatureGenerationService signatureGenerationService;

    @Mock
    private SignatureValidationEngine signatureValidationEngine;

    @Mock
    private SignaturePolicyResolver signaturePolicyResolver;

    @Mock
    private HttpOutputLegalArchivingSupport legalArchivingSupport;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private HttpOutputAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new HttpOutputAdapter(
                gatewayConfig(),
                signatureGenerationService,
                signatureValidationEngine,
                signaturePolicyResolver,
                legalArchivingSupport
        );
        setHttpClient(adapter, httpClient);
    }

    @Test
    void shouldExecuteSignedRequestAndMapValidatedResponse() {
        SignatureData requestSignatureData = new SignatureData(
                "sig1=:AQID:",
                "sig1=(\"@method\" \"request-id\")",
                Map.of("@method", "POST", "request-id", "req-1")
        );
        SignatureData responseSignatureData = new SignatureData(
                "sig2=:AQID:",
                "sig2=(\"@status\")",
                Map.of("@status", "202")
        );
        SignatureGenerationResult generationResult = new SignatureGenerationResult(
                "request-policy",
                Map.of("Signature", "sig1=:AQID:", "Request-ID", "req-1"),
                requestSignatureData
        );
        ResolvedValidationPolicy responsePolicy = resolvedValidationPolicy("response-policy");
        ValidationReport validationReport = ValidationReport.ok(
                "response-policy",
                "SYNC",
                "partner-key",
                "CN=Partner",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                responseSignatureData,
                Map.of()
        );
        RequestPayload payload = new RequestPayload();
        payload.setTarget("payment-provider");
        payload.setBody("{\"paymentId\":\"p-1\"}");
        payload.getHeaders().put("X-Correlation", "corr-1");

        when(signatureGenerationService.sign(any(OutgoingHttpMessage.class), eq("payment-provider"), eq("request-policy")))
                .thenReturn(generationResult);
        when(signaturePolicyResolver.resolveOutboundValidationOptional(any()))
                .thenReturn(Optional.of(responsePolicy));
        when(signatureValidationEngine.validate(any(SignatureContextMessage.class), eq(responsePolicy)))
                .thenReturn(validationReport);
        when(httpResponse.statusCode()).thenReturn(202);
        when(httpResponse.body()).thenReturn("{\"status\":\"accepted\"}");
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(Map.of("X-Response", List.of("ok")), (left, right) -> true));
        doReturn(CompletableFuture.completedFuture(httpResponse))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        ResponsePayload responsePayload = adapter.execute(payload).await().indefinitely();

        assertEquals(202, responsePayload.getStatus());
        assertEquals("{\"status\":\"accepted\"}", responsePayload.getBody());
        assertEquals("request-policy", responsePayload.getRequestSignatureEndpoint());
        assertEquals("response-policy", responsePayload.getResponseSignatureEndpoint());
        assertTrue(responsePayload.getResponseSignatureIssues().isEmpty());
        assertEquals(generationResult.headers(), responsePayload.getGeneratedSignatureHeaders());
        assertTrue(responsePayload.getDuration().endsWith(" ms"));

        ArgumentCaptor<OutgoingHttpMessage> messageCaptor = ArgumentCaptor.forClass(OutgoingHttpMessage.class);
        verify(signatureGenerationService).sign(messageCaptor.capture(), eq("payment-provider"), eq("request-policy"));
        OutgoingHttpMessage message = messageCaptor.getValue();
        assertEquals("POST", message.method());
        assertEquals(URI.create("https://partner.example.com/v1/payments"), message.uri());
        assertEquals("{\"paymentId\":\"p-1\"}", new String(message.body()));
        assertNotNull(message.headers().get("Request-ID"));
        assertEquals("corr-1", message.headers().get("X-Correlation"));

        ArgumentCaptor<Map<String, String>> requestHeadersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(legalArchivingSupport).archiveRequest(
                eq("POST"),
                eq(URI.create("https://partner.example.com/v1/payments")),
                requestHeadersCaptor.capture(),
                eq("{\"paymentId\":\"p-1\"}"),
                any(SignatureData.class)
        );
        assertEquals("sig1=:AQID:", requestHeadersCaptor.getValue().get("Signature"));
        assertEquals("req-1", requestHeadersCaptor.getValue().get("Request-ID"));
        verify(legalArchivingSupport).archiveResponse(
                eq("POST"),
                eq(URI.create("https://partner.example.com/v1/payments")),
                any(Map.class),
                eq("{\"status\":\"accepted\"}"),
                any(SignatureData.class)
        );
    }

    @Test
    void shouldReturnErrorPayloadWhenHttpCallFails() {
        RequestPayload payload = new RequestPayload();
        payload.setUrl("https://partner.example.com/v1/payments");
        payload.setMethod("POST");
        payload.setBody("{\"paymentId\":\"p-1\"}");
        when(signatureGenerationService.sign(any(OutgoingHttpMessage.class), eq(null), eq(null)))
                .thenReturn(new SignatureGenerationResult("direct", Map.of(), new SignatureData(null, null, Map.of())));
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("downstream-boom")))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        ResponsePayload responsePayload = adapter.execute(payload).await().indefinitely();

        assertEquals("downstream-boom", responsePayload.getError());
        assertNull(responsePayload.getStatus());
        verify(legalArchivingSupport).archiveRequest(
                eq("POST"),
                eq(URI.create("https://partner.example.com/v1/payments")),
                any(Map.class),
                eq("{\"paymentId\":\"p-1\"}"),
                any(SignatureData.class)
        );
        verify(legalArchivingSupport, never()).archiveResponse(any(), any(), any(), any(), any());
        verify(signatureValidationEngine, never()).validate(any(SignatureContextMessage.class), any(ResolvedValidationPolicy.class));
    }

    @Test
    void shouldSwallowResponseValidationExceptionsAndStillReturnResponse() {
        RequestPayload payload = new RequestPayload();
        payload.setTarget("payment-provider");
        payload.setBody("{\"paymentId\":\"p-1\"}");
        when(signatureGenerationService.sign(any(OutgoingHttpMessage.class), eq("payment-provider"), eq("request-policy")))
                .thenReturn(new SignatureGenerationResult(
                        "request-policy",
                        Map.of("Signature", "sig1=:AQID:"),
                        new SignatureData("sig1=:AQID:", "sig1=(\"@method\")", Map.of("@method", "POST"))
                ));
        when(signaturePolicyResolver.resolveOutboundValidationOptional(any()))
                .thenThrow(new IllegalStateException("validation-boom"));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"status\":\"ok\"}");
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (left, right) -> true));
        doReturn(CompletableFuture.completedFuture(httpResponse))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        ResponsePayload responsePayload = adapter.execute(payload).await().indefinitely();

        assertEquals(200, responsePayload.getStatus());
        assertEquals("{\"status\":\"ok\"}", responsePayload.getBody());
        assertNull(responsePayload.getResponseSignatureEndpoint());
        assertTrue(responsePayload.getResponseSignatureIssues().isEmpty());
        verify(legalArchivingSupport).archiveResponse(
                eq("POST"),
                eq(URI.create("https://partner.example.com/v1/payments")),
                any(Map.class),
                eq("{\"status\":\"ok\"}"),
                eq(null)
        );
    }

    @Test
    void shouldRejectMissingRequestUrlWhenNoTargetDefaultExists() {
        RequestPayload payload = new RequestPayload();
        payload.setTarget("unknown-target");

        assertThrows(SignatureConfigurationException.class, () -> adapter.execute(payload));
    }

    private GatewayConfig gatewayConfig() {
        return new GatewayConfig() {
            @Override
            public OutboundConfig outbound() {
                return new OutboundConfig() {
                    @Override
                    public Duration connectTimeout() {
                        return Duration.ofSeconds(5);
                    }

                    @Override
                    public Duration readTimeout() {
                        return Duration.ofSeconds(10);
                    }
                };
            }

            @Override
            public Map<String, TargetConfig> targets() {
                return Map.of("payment-provider", new TargetConfig() {
                    @Override
                    public Optional<String> description() {
                        return Optional.of("payment provider");
                    }

                    @Override
                    public Optional<String> baseUrl() {
                        return Optional.of("https://partner.example.com/v1/payments");
                    }

                    @Override
                    public String method() {
                        return "POST";
                    }

                    @Override
                    public Map<String, String> headers() {
                        return Map.of("X-Default", "default-value");
                    }

                    @Override
                    public Optional<String> requestSignatureEndpoint() {
                        return Optional.of("request-policy");
                    }

                    @Override
                    public Optional<String> responseSignatureEndpoint() {
                        return Optional.empty();
                    }
                });
            }
        };
    }

    private ResolvedValidationPolicy resolvedValidationPolicy(String name) {
        return new ResolvedValidationPolicy(
                name,
                false,
                false,
                List.of(SignatureConstants.HEADER_SIGNATURE),
                List.of(CoveredComponent.of(SignatureConstants.COMPONENT_STATUS)),
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                false,
                false,
                false,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                false,
                "NONE",
                true
        );
    }

    private void setHttpClient(HttpOutputAdapter adapter, HttpClient httpClient) throws Exception {
        Field field = HttpOutputAdapter.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(adapter, httpClient);
    }
}
