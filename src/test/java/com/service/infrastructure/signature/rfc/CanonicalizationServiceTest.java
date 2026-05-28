package com.service.infrastructure.signature.rfc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalizationServiceTest {

    @Test
    void shouldReturnSignatureBaseAndResolvedComponentValuesFromSameCanonicalizationPass() {
        CanonicalizationService service = new CanonicalizationService(new SignatureComponentResolver());

        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("request-id", List.of("req-123"));
        headers.put("content-digest", List.of("sha-256=:abc:"));

        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/payments",
                null,
                "example.test",
                headers,
                "{\"amount\":10}".getBytes(StandardCharsets.UTF_8),
                null
        );

        CanonicalizationResult result = service.buildSignatureBase(
                SignatureContextMessage.standalone(message),
                List.of(CoveredComponent.of("@method"), CoveredComponent.of("request-id")),
                "(\"@method\" \"request-id\");created=1"
        );

        assertEquals(
                "\"@method\": POST\n"
                        + "\"request-id\": req-123\n"
                        + "\"@signature-params\": (\"@method\" \"request-id\");created=1",
                new String(result.signatureBase(), StandardCharsets.UTF_8)
        );
        assertEquals(Map.of("@method", "POST", "request-id", "req-123"), result.componentValues());
    }

    @Test
    void shouldExposeDefensiveCopiesForSignatureBaseAndImmutableComponentMap() {
        CanonicalizationResult result = new CanonicalizationResult(
                "base".getBytes(StandardCharsets.UTF_8),
                new LinkedHashMap<>(Map.of("@method", "POST"))
        );

        byte[] signatureBase = result.signatureBase();
        signatureBase[0] = 'x';

        assertArrayEquals("base".getBytes(StandardCharsets.UTF_8), result.signatureBase());
        assertThrows(UnsupportedOperationException.class, () -> result.componentValues().put("request-id", "req-1"));
    }
}
