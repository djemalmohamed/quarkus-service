package com.service.infrastructure.adapters.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalArchivingEventProtoMapperTest {

    private final LegalArchivingEventProtoMapper mapper = new LegalArchivingEventProtoMapper();

    @Test
    void mapperProducesOnlyFieldsDefinedBySharedProtoContract() {
        byte[] payload = "{\"uetr\":\"abc\",\"amount\":10}".getBytes(StandardCharsets.UTF_8);
        byte[] signature = "sig1=:abc123=:".getBytes(StandardCharsets.UTF_8);
        LegalArchivingEvent event = new LegalArchivingEvent(
                "event-123",
                "POST /v1/payments",
                "INBOUND",
                "RESPONSE",
                payload,
                signature,
                "sig1=(\"content-type\" \"@method\");created=1704722601",
                List.of(
                        new LegalArchivingEvent.SignatureComponent("content-type", "application/json"),
                        new LegalArchivingEvent.SignatureComponent("@method", "POST")
                )
        );

        var proto = mapper.toProto(event);
        String json = proto.toJson();

        assertTrue(json.contains("\"lea_signature_data\""));
        assertTrue(json.contains("\"signature_params\""));
        assertTrue(json.contains("\"legal_core_data\""));
        assertTrue(json.contains("\"lea_additional_data\":{}"));
        assertArrayEquals(payload, proto.getLegalCoreData().getPayload().toByteArray());
        assertArrayEquals(signature, proto.getLeaSignatureData().getSignature().toByteArray());

        assertFalse(json.contains("event_id"));
        assertFalse(json.contains("request_id"));
        assertFalse(json.contains("operation"));
        assertFalse(json.contains("http_method"));
        assertFalse(json.contains("\"uri\""));
        assertFalse(json.contains("status_code"));
        assertFalse(json.contains("\"direction\""));
        assertFalse(json.contains("\"phase\""));
        assertFalse(json.contains("occurred_at"));
        assertFalse(json.contains("\"source\""));
        assertFalse(json.contains("\"target\""));
        assertFalse(json.contains("http_header"));
        assertFalse(json.contains("content_type"));
        assertFalse(json.contains("body_charset"));
        assertFalse(json.contains("binary_payload"));
        assertFalse(json.contains("additional_parameter"));
    }
}
