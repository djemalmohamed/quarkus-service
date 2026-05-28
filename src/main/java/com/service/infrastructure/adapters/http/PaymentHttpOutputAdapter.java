package com.service.infrastructure.adapters.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.application.port.out.PaymentOutputAdapter;
import com.service.domain.payment.Payment;
import com.service.domain.payment.PaymentResult;
import com.service.infrastructure.adapters.http.model.RequestPayload;
import com.service.infrastructure.adapters.http.model.ResponsePayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP implementation of the payment output adapter.
 *
 * <p>This adapter translates a domain {@link Payment} into the provider-specific HTTP payload,
 * delegates the signed request execution to {@link HttpOutputAdapter}, validates the signed
 * synchronous response and maps the technical outcome back to a domain {@link PaymentResult}.</p>
 *
 * <p>The class is the infrastructure boundary where outbound HTTP, message signing and response
 * validation are combined for the payment use case.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PaymentHttpOutputAdapter implements PaymentOutputAdapter {

    private static final String PAYMENT_PROVIDER_TARGET = "payment-provider";
    private static final String HTTP_METHOD_POST = "POST";
    private static final String METADATA_GENERATED_SIGNATURE_HEADERS = "generatedSignatureHeaders";
    private static final String METADATA_RESPONSE_SIGNATURE_ISSUES = "responseSignatureIssues";
    private static final String METADATA_ERROR = "error";

    private final HttpOutputAdapter httpOutputAdapter;

    private final ObjectMapper objectMapper;

    /**
     * Sends the payment to the configured provider using the gateway HTTP stack.
     *
     * @param payment payment to serialize and send downstream
     * @return mapped domain result containing provider status and signature outcome
     */
    @Override
    public PaymentResult process(Payment payment) {
        try {
            RequestPayload payload = buildRequestPayload(payment);
            ResponsePayload response = httpOutputAdapter.execute(payload).await().indefinitely();
            return toPaymentResult(response);
        } catch (Exception e) {
            return failureResult(e);
        }
    }

    /**
     * Builds the technical request payload expected by the reusable outbound HTTP adapter.
     *
     * @param payment domain payment request
     * @return outbound request payload ready for HTTP execution
     * @throws JsonProcessingException when JSON serialization fails
     */
    private RequestPayload buildRequestPayload(Payment payment) throws JsonProcessingException {
        RequestPayload payload = new RequestPayload();
        payload.setTarget(PAYMENT_PROVIDER_TARGET);
        payload.setMethod(HTTP_METHOD_POST);
        payload.setBody(objectMapper.writeValueAsString(toProviderRequest(payment)));
        return payload;
    }

    /**
     * Maps the low-level outbound response into the domain-facing payment result.
     *
     * @param response technical outbound response
     * @return domain payment result
     */
    private PaymentResult toPaymentResult(ResponsePayload response) {
        PaymentResult result = new PaymentResult();
        result.setAccepted(isSuccessful(response));
        result.setProviderStatus(response.getStatus());
        result.setProviderResponseBody(response.getBody());
        result.setResponseSignatureValid(response.getResponseSignatureIssues().isEmpty());
        result.setRequestSignatureEndpoint(response.getRequestSignatureEndpoint());
        result.setResponseSignatureEndpoint(response.getResponseSignatureEndpoint());
        result.getMetadata().put(METADATA_GENERATED_SIGNATURE_HEADERS, response.getGeneratedSignatureHeaders());
        result.getMetadata().put(METADATA_RESPONSE_SIGNATURE_ISSUES, response.getResponseSignatureIssues());
        if (response.getError() != null) {
            result.getMetadata().put(METADATA_ERROR, response.getError());
        }
        return result;
    }

    /**
     * Creates a domain failure result when the outbound infrastructure layer itself fails.
     *
     * @param error infrastructure failure
     * @return failure result exposing the technical error message in metadata
     */
    private PaymentResult failureResult(Exception error) {
        PaymentResult result = new PaymentResult();
        result.setAccepted(false);
        result.getMetadata().put(METADATA_ERROR, error.getMessage());
        return result;
    }

    /**
     * Determines whether the provider response should be treated as an accepted payment outcome.
     *
     * @param response technical outbound response
     * @return {@code true} when the HTTP status is successful and no transport error occurred
     */
    private boolean isSuccessful(ResponsePayload response) {
        return response.getError() == null
                && response.getStatus() != null
                && response.getStatus() >= 200
                && response.getStatus() < 300;
    }

    /**
     * Converts the domain payment object into the provider-specific JSON payload contract.
     *
     * @param payment domain payment object
     * @return ordered map ready for JSON serialization
     */
    private Map<String, Object> toProviderRequest(Payment payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.paymentId());
        payload.put("debtorIban", payment.debtorIban());
        payload.put("creditorIban", payment.creditorIban());
        payload.put("currency", payment.currency());
        payload.put("amount", payment.amount());
        payload.put("remittanceInformation", payment.remittanceInformation());
        return payload;
    }
}
