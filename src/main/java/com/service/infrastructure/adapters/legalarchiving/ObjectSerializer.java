package com.service.infrastructure.adapters.legalarchiving;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;

/**
 * Serializes archived HTTP bodies directly to the byte array expected by the legal-archiving event.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ObjectSerializer {

    private final ObjectMapper objectMapper;

    /**
     * Normalizes already materialized HTTP body bytes.
     *
     * @param payload the raw HTTP body bytes
     * @return a defensive copy of the archived payload, or {@code null} when the body is empty
     */
    public byte[] serialize(byte[] payload) {
        if (null == payload || payload.length == 0) {
            return null;
        }
        return payload.clone();
    }

    /**
     * Serializes a JAX-RS response entity to the archived payload bytes.
     *
     * <p>Byte arrays and strings are preserved directly. Structured entities are
     * serialized as JSON when possible, with a plain-text fallback reserved for
     * unexpected serialization failures.
     *
     * @param entity the HTTP entity to archive
     * @return the serialized payload bytes, or {@code null} when the entity is absent
     */
    public byte[] serialize(Object entity) {
        if (null == entity) {
            return null;
        }

        if (entity instanceof byte[] bytes) {
            return serialize(bytes);
        }

        if (entity instanceof String text) {
            return text.isEmpty() ? null : text.getBytes(StandardCharsets.UTF_8);
        }

        try {
            return serialize(objectMapper.writeValueAsBytes(entity));
        } catch (JsonProcessingException exception) {
            return entity.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
