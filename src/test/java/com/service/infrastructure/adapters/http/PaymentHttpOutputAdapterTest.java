package com.service.infrastructure.adapters.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.domain.payment.Payment;
import com.service.domain.payment.PaymentResult;
import com.service.infrastructure.adapters.http.model.ResponsePayload;
import com.service.infrastructure.signature.validation.model.SignatureValidationCode;
import com.service.infrastructure.signature.validation.model.ValidationIssue;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentHttpOutputAdapterTest {

    private final HttpOutputAdapter httpOutputAdapter = mock(HttpOutputAdapter.class);
    private final PaymentHttpOutputAdapter adapter = new PaymentHttpOutputAdapter(httpOutputAdapter, new ObjectMapper());

    @Test
    void shouldMapSuccessfulProviderResponse() {
        ResponsePayload response = new ResponsePayload();
        response.setStatus(201);
        response.setBody("{\"status\":\"accepted\"}");
        response.setRequestSignatureEndpoint("request-policy");
        response.setResponseSignatureEndpoint("response-policy");
        response.setGeneratedSignatureHeaders(java.util.Map.of("Signature", "sig=:AQID:"));
        when(httpOutputAdapter.execute(any())).thenReturn(Uni.createFrom().item(response));

        PaymentResult result = adapter.process(samplePayment());

        assertTrue(result.isAccepted());
        assertEquals(201, result.getProviderStatus());
        assertEquals("{\"status\":\"accepted\"}", result.getProviderResponseBody());
        assertTrue(result.isResponseSignatureValid());
        assertEquals("request-policy", result.getRequestSignatureEndpoint());
        assertEquals("response-policy", result.getResponseSignatureEndpoint());
        assertEquals(java.util.Map.of("Signature", "sig=:AQID:"), result.getMetadata().get("generatedSignatureHeaders"));
    }

    @Test
    void shouldMapProviderResponseWithErrorsAsRejected() {
        ResponsePayload response = new ResponsePayload();
        response.setStatus(500);
        response.setBody("{\"status\":\"rejected\"}");
        response.setError("provider-boom");
        response.getResponseSignatureIssues().add(new ValidationIssue(
                SignatureValidationCode.SIG_INVALID_SIGNATURE,
                "invalid signature",
                "signature"
        ));
        when(httpOutputAdapter.execute(any())).thenReturn(Uni.createFrom().item(response));

        PaymentResult result = adapter.process(samplePayment());

        assertFalse(result.isAccepted());
        assertFalse(result.isResponseSignatureValid());
        assertEquals("provider-boom", result.getMetadata().get("error"));
    }

    @Test
    void shouldReturnFailureResultWhenOutboundAdapterThrows() {
        when(httpOutputAdapter.execute(any())).thenReturn(Uni.createFrom().failure(new IllegalStateException("transport-down")));

        PaymentResult result = adapter.process(samplePayment());

        assertFalse(result.isAccepted());
        assertEquals("transport-down", result.getMetadata().get("error"));
    }

    private Payment samplePayment() {
        return new Payment(
                "p-1",
                "FR7611111111111111111111111",
                "FR7622222222222222222222222",
                "EUR",
                "10.00",
                "Invoice 123"
        );
    }
}
