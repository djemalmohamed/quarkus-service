package com.service.infrastructure.adapters.legalarchiving;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ObjectSerializerTest {

    @Test
    void shouldReturnNullForNullOrEmptyBytePayload() {
        ObjectSerializer serializer = new ObjectSerializer(new ObjectMapper());

        assertNull(serializer.serialize((byte[]) null));
        assertNull(serializer.serialize(new byte[0]));
    }

    @Test
    void shouldDefensivelyCopyBytePayload() {
        ObjectSerializer serializer = new ObjectSerializer(new ObjectMapper());
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        byte[] serialized = serializer.serialize(payload);
        payload[0] = 'x';

        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), serialized);
    }

    @Test
    void shouldSerializeStringAndStructuredObjects() {
        ObjectSerializer serializer = new ObjectSerializer(new ObjectMapper());

        assertNull(serializer.serialize(""));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), serializer.serialize("hello"));
        assertArrayEquals(
                "{\"status\":\"OK\"}".getBytes(StandardCharsets.UTF_8),
                serializer.serialize(Map.of("status", "OK"))
        );
    }

    @Test
    void shouldFallbackToToStringWhenJsonSerializationFails() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("boom") {
                };
            }
        };
        ObjectSerializer serializer = new ObjectSerializer(failingMapper);

        byte[] serialized = serializer.serialize(new Object() {
            @Override
            public String toString() {
                return "fallback-value";
            }
        });

        assertArrayEquals("fallback-value".getBytes(StandardCharsets.UTF_8), serialized);
    }
}
