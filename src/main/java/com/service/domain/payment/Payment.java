package com.service.domain.payment;

/**
 * Immutable domain representation of a payment handled by the gateway.
 *
 * <p>This record intentionally models only business data required by the payment use case.
 * It does not expose HTTP, signature, serialization or provider-specific transport details.</p>
 */
public record Payment(
        String paymentId,
        String debtorIban,
        String creditorIban,
        String currency,
        String amount,
        String remittanceInformation
) {
}
