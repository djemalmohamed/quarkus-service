package com.service.infrastructure.signature.rfc;

import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.ParsedSignature;
import com.service.infrastructure.signature.rfc.model.ParsedSignatureInput;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignatureInputParserTest {

    private final SignatureInputParser parser = new SignatureInputParser();

    @Test
    void shouldParseValidSignatureHeaders() {
        byte[] signatureBytes = "signed-value".getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, List.of("sig=(\"@method\" \"content-digest\");alg=\"ecdsa-p256-sha256\";keyid=\"partner-key\";created=1710000000;expires=1710000060;nonce=\"abc12345\""),
                SignatureConstants.HTTP_HEADER_SIGNATURE, List.of("sig=:" + Base64.getEncoder().encodeToString(signatureBytes) + ":")
        );

        ParsedSignatureInput signatureInput = parser.parseSignatureInput(headers);
        ParsedSignature signature = parser.parseSignature(headers, signatureInput.label());

        assertEquals("sig", signatureInput.label());
        assertEquals(List.of(CoveredComponent.of("@method"), CoveredComponent.of("content-digest")), signatureInput.components());
        assertEquals("(\"@method\" \"content-digest\");alg=\"ecdsa-p256-sha256\";keyid=\"partner-key\";created=1710000000;expires=1710000060;nonce=\"abc12345\"", signatureInput.signatureParams());
        assertEquals("ecdsa-p256-sha256", signatureInput.algorithm());
        assertEquals("partner-key", signatureInput.keyId());
        assertEquals(1710000000L, signatureInput.created());
        assertEquals(1710000060L, signatureInput.expires());
        assertEquals("abc12345", signatureInput.nonce());
        assertEquals("sig", signature.label());
        assertArrayEquals(signatureBytes, signature.signatureBytes());
    }

    @Test
    void shouldRejectMalformedCreatedParameter() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, List.of("sig=(\"@method\");created=not-a-number;keyid=\"partner-key\";alg=\"ecdsa-p256-sha256\"")
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignatureInput(headers));
    }

    @Test
    void shouldRejectInvalidBase64Signature() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE, List.of("sig=:###:")
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignature(headers, "sig"));
    }

    @Test
    void shouldParseValidSignatureHeadersWithOptionalWhitespace() {
        byte[] signatureBytes = "signed-value".getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, List.of("  sig = ( \"@method\"   \"content-digest\" ) ; alg = \"ecdsa-p256-sha256\" ; keyid = \"partner-key\" ; created = 1710000000  "),
                SignatureConstants.HTTP_HEADER_SIGNATURE, List.of("  sig = :" + Base64.getEncoder().encodeToString(signatureBytes) + ":  ")
        );

        ParsedSignatureInput signatureInput = parser.parseSignatureInput(headers);
        ParsedSignature signature = parser.parseSignature(headers, signatureInput.label());

        assertEquals("sig", signatureInput.label());
        assertEquals(List.of(CoveredComponent.of("@method"), CoveredComponent.of("content-digest")), signatureInput.components());
        assertEquals("ecdsa-p256-sha256", signatureInput.algorithm());
        assertEquals("partner-key", signatureInput.keyId());
        assertEquals(1710000000L, signatureInput.created());
        assertArrayEquals(signatureBytes, signature.signatureBytes());
    }

    @Test
    void shouldParseRequestBoundCoveredComponents() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT,
                List.of("sig=(\"content-digest\" \"response-timestamp\" \"@status\" \"signature\";req \"request-id\";req);alg=\"ecdsa-p256-sha256\";keyid=\"agc\";created=1710000000")
        );

        ParsedSignatureInput signatureInput = parser.parseSignatureInput(headers);

        assertEquals(
                List.of(
                        CoveredComponent.of("content-digest"),
                        CoveredComponent.of("response-timestamp"),
                        CoveredComponent.of("@status"),
                        CoveredComponent.request("signature"),
                        CoveredComponent.request("request-id")
                ),
                signatureInput.components()
        );
    }

    @Test
    void shouldRejectTrailingDataAfterSignatureValue() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE, List.of("sig=:dGVzdA==:;extra=true")
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignature(headers, "sig"));
    }

    @Test
    void shouldRejectMultipleSignatureInputHeaderValues() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, List.of(
                        "sig=(\"@method\");created=1710000000;keyid=\"partner-key\";alg=\"ecdsa-p256-sha256\"",
                        "sig2=(\"@path\");created=1710000000;keyid=\"partner-key\";alg=\"ecdsa-p256-sha256\""
                )
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignatureInput(headers));
    }

    @Test
    void shouldRejectMultipleSignatureInputEntriesInsideSingleHeaderValue() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, List.of(
                        "sig=(\"@method\");created=1710000000;keyid=\"partner-key\";alg=\"ecdsa-p256-sha256\", sig2=(\"@path\");created=1710000000;keyid=\"partner-key\";alg=\"ecdsa-p256-sha256\""
                )
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignatureInput(headers));
    }

    @Test
    void shouldRejectMultipleSignatureHeaderValues() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE, List.of(
                        "sig=:dGVzdA==:",
                        "sig2=:dGVzdDI=:"
                )
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignature(headers, "sig"));
    }

    @Test
    void shouldRejectMultipleSignatureEntriesInsideSingleHeaderValue() {
        Map<String, List<String>> headers = Map.of(
                SignatureConstants.HTTP_HEADER_SIGNATURE, List.of(
                        "sig=:dGVzdA==:, sig2=:dGVzdDI=:"
                )
        );

        assertThrows(SignatureInfrastructureException.class, () -> parser.parseSignature(headers, "sig"));
    }
}
