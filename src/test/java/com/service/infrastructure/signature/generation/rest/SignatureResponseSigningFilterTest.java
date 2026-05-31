package com.service.infrastructure.signature.generation.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.generation.service.SignatureGenerationService;
import com.service.infrastructure.signature.rfc.MissingCoveredComponentException;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureResponseSigningFilterTest {

    @Mock
    private SignatureGenerationService signatureGenerationService;

    @Mock
    private SignaturePolicyResolver policyResolver;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private ContainerResponseContext responseContext;

    @Mock
    private UriInfo uriInfo;

    private SignatureResponseSigningFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SignatureResponseSigningFilter(signatureGenerationService, policyResolver, new ObjectMapper());
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
    }

    @Test
    void shouldIgnoreInternalQuarkusPath() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://gateway.example.com/q/health"));

        filter.filter(requestContext, responseContext);

        verify(policyResolver, never()).resolveInboundGeneration(any(), any(OutgoingHttpMessage.class));
        verify(signatureGenerationService, never()).sign(any(SignatureContextMessage.class), any(ResolvedGenerationPolicy.class));
    }

    @Test
    void shouldSignResponseWhenPolicyApplies() {
        MultivaluedHashMap<String, String> requestHeaders = new MultivaluedHashMap<>();
        requestHeaders.add("Request-ID", "req-1");
        MultivaluedHashMap<String, Object> responseHeaders = new MultivaluedHashMap<>();
        ResolvedGenerationPolicy policy = responsePolicy();

        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://gateway.example.com/v1/payments"));
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getHeaders()).thenReturn(requestHeaders);
        when(responseContext.getHeaders()).thenReturn(responseHeaders);
        when(responseContext.getEntity()).thenReturn(Map.of("status", "ok"));
        when(responseContext.getStatus()).thenReturn(200);
        when(policyResolver.resolveInboundGeneration(eq(null), any(OutgoingHttpMessage.class))).thenReturn(Optional.of(policy));
        when(signatureGenerationService.sign(any(SignatureContextMessage.class), eq(policy))).thenReturn(
                new SignatureGenerationResult(
                        "response-policy",
                        Map.of("Signature", "sig=:AQID:", "Signature-Input", "sig=(\"@status\")")
                )
        );

        filter.filter(requestContext, responseContext);

        assertEquals("sig=:AQID:", responseHeaders.getFirst("Signature"));
        assertEquals("sig=(\"@status\")", responseHeaders.getFirst("Signature-Input"));
        assertNotNull(responseHeaders.getFirst("Response-Timestamp"));

        ArgumentCaptor<SignatureContextMessage> contextCaptor = ArgumentCaptor.forClass(SignatureContextMessage.class);
        verify(signatureGenerationService).sign(contextCaptor.capture(), eq(policy));
        SignatureContextMessage signatureContext = contextCaptor.getValue();
        assertNotNull(signatureContext.associatedRequest());
        assertTrue(signatureContext.currentMessage() instanceof OutgoingHttpMessage);
    }

    @Test
    void shouldLeaveResponseUnsignedWhenCoveredComponentIsMissing() {
        MultivaluedHashMap<String, String> requestHeaders = new MultivaluedHashMap<>();
        MultivaluedHashMap<String, Object> responseHeaders = new MultivaluedHashMap<>();
        ResolvedGenerationPolicy policy = responsePolicy();

        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://gateway.example.com/v1/payments"));
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getHeaders()).thenReturn(requestHeaders);
        when(responseContext.getHeaders()).thenReturn(responseHeaders);
        when(responseContext.getEntity()).thenReturn("ok");
        when(responseContext.getStatus()).thenReturn(200);
        when(policyResolver.resolveInboundGeneration(eq(null), any(OutgoingHttpMessage.class))).thenReturn(Optional.of(policy));
        when(signatureGenerationService.sign(any(SignatureContextMessage.class), eq(policy)))
                .thenThrow(new MissingCoveredComponentException("@status"));

        filter.filter(requestContext, responseContext);

        assertEquals(null, responseHeaders.getFirst("Signature"));
        assertNotNull(responseHeaders.getFirst("Response-Timestamp"));
    }

    private ResolvedGenerationPolicy responsePolicy() {
        return new ResolvedGenerationPolicy(
                "response-policy",
                "sig",
                "gateway-key",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                List.of(
                        CoveredComponent.of(SignatureConstants.HEADER_RESPONSE_TIMESTAMP),
                        CoveredComponent.of(SignatureConstants.COMPONENT_STATUS),
                        CoveredComponent.request(SignatureConstants.HEADER_REQUEST_ID)
                ),
                false,
                true,
                false,
                Duration.ofMinutes(5),
                false,
                0
        );
    }
}
