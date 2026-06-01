package com.service.infrastructure.signature.generation.model;

import com.service.infrastructure.signature.validation.model.SignatureData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignatureGenerationResultTest {

    @Test
    void shouldCopyHeadersAndNestedSignatureData() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Signature", "sig=:abc:");

        byte[] signature = "sig=:abc:".getBytes(StandardCharsets.UTF_8);
        SignatureData signatureData = new SignatureData(
                signature,
                "sig=(\"@method\")",
                Map.of("@method", "POST")
        );

        SignatureGenerationResult result = new SignatureGenerationResult("common", headers, signatureData);

        headers.put("X-Test", "mutated");
        signature[0] = 'x';

        assertEquals(Map.of("Signature", "sig=:abc:"), result.headers());
        assertThrows(UnsupportedOperationException.class, () -> result.headers().put("x", "y"));
        assertArrayEquals("sig=:abc:".getBytes(StandardCharsets.UTF_8), result.signatureData().signature());
        assertEquals(Map.of("@method", "POST"), result.signatureData().componentValues());
    }

    @Test
    void shouldSupportConvenienceConstructorWithoutSignatureData() {
        SignatureGenerationResult result = new SignatureGenerationResult(
                "common",
                Map.of("Signature-Input", "sig=(\"@method\")")
        );

        assertEquals("common", result.policy());
        assertEquals(Map.of("Signature-Input", "sig=(\"@method\")"), result.headers());
        assertNull(result.signatureData());
    }
}
