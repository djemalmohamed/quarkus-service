package com.service.infrastructure.signature.rfc;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalizationResultTest {

    @Test
    void shouldDefensivelyCopySignatureBaseAndExposeImmutableComponentValues() {
        byte[] signatureBase = "base".getBytes(StandardCharsets.UTF_8);
        Map<String, String> componentValues = new LinkedHashMap<>();
        componentValues.put("@method", "POST");

        CanonicalizationResult result = new CanonicalizationResult(signatureBase, componentValues);

        signatureBase[0] = 'x';
        componentValues.put("request-id", "req-1");

        assertArrayEquals("base".getBytes(StandardCharsets.UTF_8), result.signatureBase());
        assertEquals(Map.of("@method", "POST"), result.componentValues());
        assertThrows(UnsupportedOperationException.class, () -> result.componentValues().put("x", "y"));
    }

    @Test
    void shouldImplementValueSemanticsForArraysAndToString() {
        CanonicalizationResult left = new CanonicalizationResult(
                "base".getBytes(StandardCharsets.UTF_8),
                Map.of("@method", "POST")
        );
        CanonicalizationResult right = new CanonicalizationResult(
                "base".getBytes(StandardCharsets.UTF_8),
                Map.of("@method", "POST")
        );
        CanonicalizationResult different = new CanonicalizationResult(
                "other".getBytes(StandardCharsets.UTF_8),
                Map.of("@method", "POST")
        );

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().contains("signatureBaseLength=4"));
        assertTrue(left.toString().contains("@method"));
    }
}
