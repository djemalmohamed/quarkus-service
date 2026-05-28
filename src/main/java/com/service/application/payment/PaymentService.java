package com.service.application.payment;

import com.service.application.port.in.ProcessPaymentUseCase;
import com.service.application.port.out.PaymentOutputAdapter;
import com.service.domain.payment.Payment;
import com.service.domain.payment.PaymentResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/**
 * Default application service for the payment use case.
 *
 * <p>This service contains the application-level orchestration for payment processing.
 * It accepts a domain {@link Payment}, delegates external communication to the configured
 * {@link PaymentOutputAdapter} and returns a domain {@link PaymentResult}. The class is
 * intentionally free from transport, cryptographic and persistence details.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PaymentService implements ProcessPaymentUseCase {

    private final PaymentOutputAdapter paymentOutputAdapter;

    /**
     * Executes the payment use case for a single payment.
     *
     * @param payment payment to process
     * @return domain result returned by the outbound adapter
     */
    @Override
    public PaymentResult process(Payment payment) {
        return paymentOutputAdapter.process(payment);
    }
}
