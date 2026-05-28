package com.playground.gateway.application.payment;

import com.playground.gateway.application.port.out.PaymentOutputAdapter;
import com.playground.gateway.domain.payment.Payment;
import com.playground.gateway.domain.payment.PaymentResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class PaymentServiceTest {

    @Test
    void shouldDelegateProcessingToOutputAdapter() {
        PaymentResult expectedResult = new PaymentResult();
        expectedResult.setAccepted(true);

        PaymentService service = new PaymentService(new PaymentOutputAdapter() {
            @Override
            public PaymentResult process(Payment payment) {
                return expectedResult;
            }
        });

        PaymentResult actualResult = service.process(new Payment("p-1", "debtor", "creditor", "EUR", "10.00", "invoice"));

        assertSame(expectedResult, actualResult);
    }
}
