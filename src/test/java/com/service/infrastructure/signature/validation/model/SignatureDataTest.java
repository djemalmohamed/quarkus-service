package com.service.infrastructure.signature.validation.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignatureDataTest {

    @Test
    void shouldDefensivelyCopySignatureAndComponentValues() {
        byte[] signature = "sig=:abc:".getBytes(StandardCharsets.UTF_8);
        Map<String, String> componentValues = new LinkedHashMap<>();
        componentValues.put("@method", "POST");

        SignatureData signatureData = new SignatureData(signature, "sig=(\"@method\")", componentValues);

        signature[0] = 'x';
        componentValues.put("request-id", "req-1");

        assertArrayEquals("sig=:abc:".getBytes(StandardCharsets.UTF_8), signatureData.signature());
        assertEquals(Map.of("@method", "POST"), signatureData.componentValues());
        assertThrows(UnsupportedOperationException.class, () -> signatureData.componentValues().put("x", "y"));
    }

    @Test
    void shouldReturnDefensiveCopyFromAccessorAndCopyMethod() {
        SignatureData signatureData = new SignatureData(
                "sig=:abc:".getBytes(StandardCharsets.UTF_8),
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );

        byte[] firstRead = signatureData.signature();
        byte[] secondRead = signatureData.signature();
        SignatureData copied = signatureData.copy();

        firstRead[0] = 'x';

        assertNotSame(firstRead, secondRead);
        assertArrayEquals("sig=:abc:".getBytes(StandardCharsets.UTF_8), secondRead);
        assertNotSame(signatureData.signature(), copied.signature());
        assertEquals(signatureData.componentValues(), copied.componentValues());
        assertEquals(signatureData.signatureInput(), copied.signatureInput());
    }

    @Test
    void shouldImplementValueSemanticsForByteArraysAndToString() {
        SignatureData left = new SignatureData(
                "sig=:abc:".getBytes(StandardCharsets.UTF_8),
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );
        SignatureData right = new SignatureData(
                "sig=:abc:".getBytes(StandardCharsets.UTF_8),
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );
        SignatureData different = new SignatureData(
                "sig=:xyz:".getBytes(StandardCharsets.UTF_8),
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().contains("signatureLength=9"));
        assertTrue(left.toString().contains("@method"));
    }
}
