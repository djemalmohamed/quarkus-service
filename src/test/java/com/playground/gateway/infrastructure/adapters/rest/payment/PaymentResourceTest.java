package com.playground.gateway.infrastructure.adapters.rest.payment;

import com.playground.gateway.application.port.in.ProcessPaymentUseCase;
import com.playground.gateway.domain.payment.Payment;
import com.playground.gateway.domain.payment.PaymentResult;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentResourceTest {

    @Test
    void shouldRejectMissingRequestBody() {
        PaymentResource resource = new PaymentResource(payment -> new PaymentResult());

        BadRequestException error = assertThrows(BadRequestException.class, () -> resource.process(null));

        assertTrue(error.getMessage().contains("Payment request body is required"));
    }

    @Test
    void shouldDelegateMappedDomainPaymentToUseCase() {
        AtomicReference<Payment> capturedPayment = new AtomicReference<>();
        PaymentResult expected = new PaymentResult();
        expected.setAccepted(true);
        expected.setProviderStatus(202);

        PaymentResource resource = new PaymentResource(new ProcessPaymentUseCase() {
            @Override
            public PaymentResult process(Payment payment) {
                capturedPayment.set(payment);
                return expected;
            }
        });

        PaymentRequest request = new PaymentRequest();
        request.setPaymentId("p-1");
        request.setDebtorIban("FR761234");
        request.setCreditorIban("FR769876");
        request.setCurrency("EUR");
        request.setAmount("10.00");
        request.setRemittanceInformation("invoice");

        PaymentResult actual = resource.process(request);

        assertSame(expected, actual);
        assertEquals(new Payment("p-1", "FR761234", "FR769876", "EUR", "10.00", "invoice"), capturedPayment.get());
    }
}
