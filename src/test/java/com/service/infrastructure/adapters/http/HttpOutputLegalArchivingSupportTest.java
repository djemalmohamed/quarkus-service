package com.service.infrastructure.adapters.http;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.legalarchiving.policy.LegalArchivingPolicy;
import com.service.application.port.in.LegalArchivingInPort;
import com.service.infrastructure.adapters.legalarchiving.LegalArchivingEventMapper;
import com.service.infrastructure.signature.validation.model.SignatureData;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HttpOutputLegalArchivingSupportTest {

    @Test
    void shouldArchiveOutboundRequestWhenPolicyApplies() {
        LegalArchivingPolicy policy = mock(LegalArchivingPolicy.class);
        LegalArchivingInPort inPort = mock(LegalArchivingInPort.class);
        when(policy.shouldArchive(any())).thenReturn(true);
        when(policy.resolveOperation(any())).thenReturn("POST partner.example.com/v1/payments");
        when(inPort.archive(any())).thenReturn(Uni.createFrom().voidItem());
        HttpOutputLegalArchivingSupport support = new HttpOutputLegalArchivingSupport(
                policy,
                new LegalArchivingEventMapper(),
                inPort
        );

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("REQUEST-ID", "req-1");
        SignatureData signatureData = new SignatureData(
                "sig1=:AQID:",
                "sig1=(\"@method\" \"request-id\")",
                Map.of("@method", "POST", "request-id", "req-1")
        );

        support.archiveRequest(
                "POST",
                URI.create("https://partner.example.com/v1/payments"),
                headers,
                "{\"paymentId\":\"p-1\"}",
                signatureData
        );

        ArgumentCaptor<LegalArchivingEvent> eventCaptor = ArgumentCaptor.forClass(LegalArchivingEvent.class);
        verify(inPort).archive(eventCaptor.capture());
        LegalArchivingEvent event = eventCaptor.getValue();
        assertEquals("req-1", event.eventId());
        assertEquals("POST partner.example.com/v1/payments", event.operation());
        assertEquals("OUTBOUND", event.direction());
        assertEquals("REQUEST", event.phase());
        assertEquals("POST", event.httpMethod());
        assertEquals("/v1/payments", event.httpPath());
        assertTrue(event.hasPayload());
        assertTrue(event.hasSignatureData());
        assertEquals(2, event.signatureComponents().size());
    }

    @Test
    void shouldSkipOutboundArchivingWhenPolicyRejectsIt() {
        LegalArchivingPolicy policy = mock(LegalArchivingPolicy.class);
        LegalArchivingInPort inPort = mock(LegalArchivingInPort.class);
        when(policy.shouldArchive(any())).thenReturn(false);
        HttpOutputLegalArchivingSupport support = new HttpOutputLegalArchivingSupport(
                policy,
                new LegalArchivingEventMapper(),
                inPort
        );

        support.archiveRequest("GET", URI.create("https://partner.example.com/health"), Map.of(), null, null);

        verifyNoInteractions(inPort);
    }

    @Test
    void shouldGenerateEventIdAndArchiveResponseWithoutPayloadOrSignatureData() {
        LegalArchivingPolicy policy = mock(LegalArchivingPolicy.class);
        LegalArchivingInPort inPort = mock(LegalArchivingInPort.class);
        when(policy.shouldArchive(any())).thenReturn(true);
        when(policy.resolveOperation(any())).thenReturn("GET partner.example.com/v1/payments");
        when(inPort.archive(any())).thenReturn(Uni.createFrom().voidItem());
        HttpOutputLegalArchivingSupport support = new HttpOutputLegalArchivingSupport(
                policy,
                new LegalArchivingEventMapper(),
                inPort
        );

        support.archiveResponse(
                "GET",
                URI.create("https://partner.example.com/v1/payments"),
                Map.of(),
                null,
                null
        );

        ArgumentCaptor<LegalArchivingEvent> eventCaptor = ArgumentCaptor.forClass(LegalArchivingEvent.class);
        verify(inPort).archive(eventCaptor.capture());
        LegalArchivingEvent event = eventCaptor.getValue();
        assertNotNull(event.eventId());
        assertEquals("RESPONSE", event.phase());
        assertEquals("GET", event.httpMethod());
        assertEquals("/v1/payments", event.httpPath());
        assertFalse(event.hasPayload());
        assertFalse(event.hasSignatureData());
    }
}
