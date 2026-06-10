package com.service.infrastructure.signature.validation.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureDataTest {

    @Test
    void shouldDefensivelyCopyComponentValues() {
        Map<String, String> componentValues = new LinkedHashMap<>();
        componentValues.put("@method", "POST");

        SignatureData signatureData = new SignatureData("sig=:abc:", "sig=(\"@method\")", componentValues);

        componentValues.put("request-id", "req-1");

        assertEquals("sig=:abc:", signatureData.signature());
        assertEquals(Map.of("@method", "POST"), signatureData.componentValues());
        assertThrows(UnsupportedOperationException.class, () -> signatureData.componentValues().put("x", "y"));
    }

    @Test
    void shouldReturnCopyFromCopyMethod() {
        SignatureData signatureData = new SignatureData(
                "sig=:abc:",
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );

        SignatureData copied = signatureData.copy();

        assertEquals(signatureData.signature(), copied.signature());
        assertEquals(signatureData.componentValues(), copied.componentValues());
        assertEquals(signatureData.signatureInput(), copied.signatureInput());
    }

    @Test
    void shouldImplementValueSemanticsAndToString() {
        SignatureData left = new SignatureData(
                "sig=:abc:",
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );
        SignatureData right = new SignatureData(
                "sig=:abc:",
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );
        SignatureData different = new SignatureData(
                "sig=:xyz:",
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().contains("signature='sig=:abc:'"));
        assertTrue(left.toString().contains("@method"));
    }
}
