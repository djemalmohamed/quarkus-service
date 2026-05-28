package com.playground.gateway.application.port.in;

import com.playground.gateway.domain.payment.Payment;
import com.playground.gateway.domain.payment.PaymentResult;

/**
 * Primary inbound application port for payment processing.
 *
 * <p>This contract is used by inbound adapters such as REST controllers to trigger
 * the payment use case without depending on any infrastructure concern. Implementations
 * are responsible for orchestrating the application flow and delegating outbound work
 * to the configured output adapter.</p>
 */
public interface ProcessPaymentUseCase {

    /**
     * Processes a payment request received by the gateway.
     *
     * @param payment immutable domain representation of the payment to process
     * @return the business result produced by the gateway after provider interaction
     */
    PaymentResult process(Payment payment);
}
