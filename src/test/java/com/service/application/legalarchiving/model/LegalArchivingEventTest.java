package com.service.application.legalarchiving.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalArchivingEventTest {

    @Test
    void shouldDefensivelyCopyPayloadAndComponents() {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        List<LegalArchivingEvent.SignatureComponent> components = new ArrayList<>();
        components.add(new LegalArchivingEvent.SignatureComponent("@method", "POST"));

        LegalArchivingEvent event = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                payload,
                "sig=:abc:",
                "sig=(\"@method\")",
                components
        );

        payload[0] = 'x';
        components.add(new LegalArchivingEvent.SignatureComponent("request-id", "req-1"));

        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), event.payload());
        assertEquals("POST", event.http().method());
        assertEquals("/v1/payments", event.http().path());
        assertEquals("sig=:abc:", event.signature());
        assertEquals(List.of(new LegalArchivingEvent.SignatureComponent("@method", "POST")), event.signatureComponents());
    }

    @Test
    void shouldExposeHelpersBasedOnCapturedData() {
        LegalArchivingEvent requestEvent = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                "payload".getBytes(StandardCharsets.UTF_8),
                null,
                "sig=(\"@method\")",
                List.of()
        );

        LegalArchivingEvent emptyEvent = new LegalArchivingEvent(
                "event-2",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                null,
                null,
                "   ",
                null
        );

        byte[] firstPayload = requestEvent.payload();
        byte[] secondPayload = requestEvent.payload();

        firstPayload[0] = 'x';

        assertTrue(requestEvent.hasPayload());
        assertTrue(requestEvent.hasSignatureData());
        assertFalse(emptyEvent.hasPayload());
        assertFalse(emptyEvent.hasSignatureData());
        assertNotSame(firstPayload, secondPayload);
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), secondPayload);
        assertEquals(List.of(), emptyEvent.signatureComponents());
    }

    @Test
    void shouldImplementValueSemanticsAndToString() {
        LegalArchivingEvent left = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                "payload".getBytes(StandardCharsets.UTF_8),
                "sig=:abc:",
                "sig=(\"@method\")",
                List.of(new LegalArchivingEvent.SignatureComponent("@method", "POST"))
        );
        LegalArchivingEvent right = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                "payload".getBytes(StandardCharsets.UTF_8),
                "sig=:abc:",
                "sig=(\"@method\")",
                List.of(new LegalArchivingEvent.SignatureComponent("@method", "POST"))
        );
        LegalArchivingEvent different = new LegalArchivingEvent(
                "event-2",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                "payload".getBytes(StandardCharsets.UTF_8),
                "sig=:abc:",
                "sig=(\"@method\")",
                List.of(new LegalArchivingEvent.SignatureComponent("@method", "POST"))
        );

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().contains("eventId='event-1'"));
        assertTrue(left.toString().contains("HttpContext[method=POST, path=/v1/payments]"));
        assertTrue(left.toString().contains("payloadLength=7"));
    }
}
