package com.service.application.legalarchiving.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalArchivingEventTest {

    @Test
    void shouldDefensivelyCopyPayloadSignatureAndComponents() {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        byte[] signature = "sig=:abc:".getBytes(StandardCharsets.UTF_8);
        List<LegalArchivingEvent.SignatureComponent> components = new ArrayList<>();
        components.add(new LegalArchivingEvent.SignatureComponent("@method", "POST"));

        LegalArchivingEvent event = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                payload,
                signature,
                "sig=(\"@method\")",
                components
        );

        payload[0] = 'x';
        signature[0] = 'x';
        components.add(new LegalArchivingEvent.SignatureComponent("request-id", "req-1"));

        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), event.payload());
        assertArrayEquals("sig=:abc:".getBytes(StandardCharsets.UTF_8), event.signature());
        assertEquals(List.of(new LegalArchivingEvent.SignatureComponent("@method", "POST")), event.signatureComponents());
    }

    @Test
    void shouldExposeHelpersBasedOnCapturedData() {
        LegalArchivingEvent requestEvent = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
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
}
