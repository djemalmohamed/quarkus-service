package com.playground.gateway.application.port.out;

import com.playground.gateway.domain.payment.Payment;
import com.playground.gateway.domain.payment.PaymentResult;

/**
 * Outbound application port used by the application layer to communicate with
 * an external payment provider.
 *
 * <p>The interface deliberately hides transport details such as HTTP, signatures,
 * headers or TLS from the application service. Concrete implementations belong
 * to the infrastructure layer.</p>
 */
public interface PaymentOutputAdapter {

    /**
     * Sends the given payment to the configured external provider.
     *
     * @param payment payment to send downstream
     * @return the result collected from the provider interaction
     */
    PaymentResult process(Payment payment);
}
