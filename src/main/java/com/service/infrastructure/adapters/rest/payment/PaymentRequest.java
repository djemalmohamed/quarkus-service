package com.service.infrastructure.adapters.rest.payment;

import com.service.domain.payment.Payment;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * REST request payload accepted by the payment endpoint.
 *
 * <p>This DTO exists to keep HTTP concerns inside the REST adapter layer. Once validated and
 * deserialized, it is converted into the immutable domain {@link Payment} record.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequest {
    private String paymentId;
    private String debtorIban;
    private String creditorIban;
    private String currency;
    private String amount;
    private String remittanceInformation;

    /**
     * Converts the REST DTO into the domain payment object used by the application layer.
     *
     * @return immutable domain payment
     */
    public Payment toDomain() {
        return new Payment(paymentId, debtorIban, creditorIban, currency, amount, remittanceInformation);
    }
}
