package com.service.infrastructure.adapters.rest.payment;

import com.service.application.port.in.ProcessPaymentUseCase;
import com.service.domain.payment.PaymentResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

/**
 * REST inbound adapter exposing the payment use case.
 *
 * <p>This resource is the single business HTTP entry point for payment processing.
 * Signature validation is handled by infrastructure filters before the request reaches
 * this adapter, allowing the resource to focus on request-to-domain mapping and use case
 * invocation.</p>
 */
@Path("/v1/payments-instructions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PaymentResource {

    private final ProcessPaymentUseCase processPaymentUseCase;

    /**
     * Handles one payment request after the infrastructure validation filters have run.
     *
     * @param request inbound REST request payload
     * @return domain payment result serialized back to the caller
     */
    @POST
    public PaymentResult process(PaymentRequest request) {
        if (request == null) {
            throw new BadRequestException("Payment request body is required");
        }
        return processPaymentUseCase.process(request.toDomain());
    }
}
