package com.service.infrastructure.adapters.legalarchiving;

import com.service.infrastructure.signature.validation.model.SignatureData;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegalArchivingEventMapperTest {

    private final LegalArchivingEventMapper mapper = new LegalArchivingEventMapper();

    @Test
    void shouldMapPreparedSignatureDataToApplicationEvent() {
        var componentValues = new LinkedHashMap<String, String>();
        componentValues.put("@method", "POST");
        componentValues.put("request-id", "req-1");
        SignatureData signatureData = new SignatureData(
                "sig1=:AQID:",
                "sig1=(\"@method\" \"request-id\")",
                componentValues
        );

        var event = mapper.toEvent(
                "req-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                new byte[]{9, 8, 7},
                signatureData
        );

        assertEquals("req-1", event.eventId());
        assertEquals("INBOUND", event.direction());
        assertEquals("REQUEST", event.phase());
        assertEquals("POST", event.httpMethod());
        assertEquals("/v1/payments", event.httpPath());
        assertArrayEquals(new byte[]{9, 8, 7}, event.payload());
        assertEquals("sig1=:AQID:", event.signature());
        assertEquals("sig1=(\"@method\" \"request-id\")", event.signatureInput());
        assertEquals(2, event.signatureComponents().size());
        assertEquals("@method", event.signatureComponents().get(0).key());
        assertEquals("POST", event.signatureComponents().get(0).value());
        assertEquals("request-id", event.signatureComponents().get(1).key());
        assertEquals("req-1", event.signatureComponents().get(1).value());
    }

    @Test
    void shouldHandleMissingSignatureData() {
        var event = mapper.toEvent(
                "req-2",
                "GET /status",
                "OUTBOUND",
                "RESPONSE",
                "GET",
                "/status",
                null,
                null
        );

        assertEquals("OUTBOUND", event.direction());
        assertFalse(event.hasPayload());
        assertFalse(event.hasSignatureData());
        assertEquals(0, event.signatureComponents().size());
    }
}
