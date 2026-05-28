package com.playground.gateway.domain.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain result returned after the gateway completes payment processing.
 *
 * <p>The object carries the business outcome together with technical metadata that is still
 * useful to the caller, such as provider status and signature validation outcome. It remains
 * part of the domain contract exposed by the application layer.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class PaymentResult {
    private boolean accepted;
    private Integer providerStatus;
    private String providerResponseBody;
    private boolean responseSignatureValid;
    private String requestSignatureEndpoint;
    private String responseSignatureEndpoint;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
