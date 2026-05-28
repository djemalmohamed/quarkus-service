package com.service.infrastructure.signature.generation.service;

import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.rfc.CanonicalizationService;
import com.service.infrastructure.signature.rfc.ContentDigestService;
import com.service.infrastructure.signature.rfc.EcdsaP256SignatureCodec;
import com.service.infrastructure.signature.rfc.SignatureComponentResolver;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import com.service.infrastructure.signature.shared.service.SignatureKeyMaterialResolver;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSignatureGenerationServiceTest {

    @Test
    void shouldGenerateResolvedSignatureHeadersWithoutDigestWhenPolicyDoesNotRequireIt() {
        DefaultSignatureGenerationService service = serviceWithKey(null);

        SignatureGenerationResult result = service.sign(message(), policyWithoutDigest());

        assertEquals("common-outbound", result.policy());
        assertFalse(result.headers().containsKey(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST));
        assertTrue(result.headers().containsKey(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT));
        assertTrue(result.headers().containsKey(SignatureConstants.HTTP_HEADER_SIGNATURE));
        assertNotNull(result.signatureData());
        assertEquals("POST", result.signatureData().componentValues().get("@method"));
        assertEquals("/v1/payments-instructions", result.signatureData().componentValues().get("@path"));
    }

    @Test
    void shouldGenerateSignatureHeadersWithDigestAndNonce() throws Exception {
        KeyPair keyPair = keyPair();
        ResolvedGenerationPolicy policy = new ResolvedGenerationPolicy(
                "common-outbound",
                "sig",
                "agc",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                List.of(
                        CoveredComponent.of("content-digest"),
                        CoveredComponent.of("content-length"),
                        CoveredComponent.of("content-type"),
                        CoveredComponent.of("@method"),
                        CoveredComponent.of("@path")
                ),
                true,
                true,
                true,
                Duration.ofMinutes(5),
                true,
                24
        );

        DefaultSignatureGenerationService service = serviceWithKey(keyPair);

        SignatureGenerationResult result = service.sign(message(), policy);

        assertEquals("common-outbound", result.policy());
        assertTrue(result.headers().containsKey(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST));
        assertTrue(result.headers().containsKey(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT));
        assertTrue(result.headers().containsKey(SignatureConstants.HTTP_HEADER_SIGNATURE));
        assertNotNull(result.signatureData());
        assertEquals("application/json", result.signatureData().componentValues().get("content-type"));
        assertEquals("POST", result.signatureData().componentValues().get("@method"));
        assertTrue(result.headers().get(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT).contains("created="));
        assertTrue(result.headers().get(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT).contains("expires="));
        assertTrue(result.headers().get(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT).contains("nonce=\""));

        String signatureHeader = result.headers().get(SignatureConstants.HTTP_HEADER_SIGNATURE);
        String encoded = signatureHeader.substring("sig=:".length(), signatureHeader.length() - 1);
        assertEquals(64, Base64.getDecoder().decode(encoded).length);
    }

    @Test
    void shouldGenerateResolvedSignatureFromContextAndEnforceMinimumNonceLength() {
        ResolvedGenerationPolicy policy = new ResolvedGenerationPolicy(
                "common-outbound",
                "sig",
                "agc",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                List.of(CoveredComponent.of("@method"), CoveredComponent.of("@path")),
                false,
                false,
                true,
                Duration.ofMinutes(5),
                true,
                4
        );

        DefaultSignatureGenerationService service = serviceWithKey(null);

        SignatureGenerationResult result = service.sign(SignatureContextMessage.standalone(message()), policy);

        String signatureInput = result.headers().get(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT);
        int nonceStart = signatureInput.indexOf("nonce=\"");
        int nonceEnd = signatureInput.indexOf("\"", nonceStart + 7);
        String nonce = signatureInput.substring(nonceStart + 7, nonceEnd);

        assertEquals("common-outbound", result.policy());
        assertEquals(8, nonce.length());
    }

    @Test
    void shouldRejectUnknownSigningKey() {
        DefaultSignatureGenerationService service = new DefaultSignatureGenerationService(
                new SignaturePolicyResolver(null, null),
                new SignatureKeyMaterialResolver(null, null, null) {
                    @Override
                    public Optional<ResolvedKeyMaterial> resolveForSigning(String keyId) {
                        return Optional.empty();
                    }
                },
                new ContentDigestService(),
                new CanonicalizationService(new SignatureComponentResolver()),
                new SignatureComponentResolver(),
                new CryptoSigningService(new EcdsaP256SignatureCodec())
        );

        assertThrows(SignatureConfigurationException.class, () -> service.sign(message(), policyWithoutDigest()));
    }

    @Test
    void shouldRejectNonOutgoingCurrentMessage() {
        DefaultSignatureGenerationService service = serviceWithKey(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.sign(
                        SignatureContextMessage.standalone(
                                new com.service.infrastructure.signature.validation.model.IncomingHttpMessage(
                                        "POST", "/v1/payments-instructions", null, "partner.example.com", Map.of(), new byte[0], null
                                )
                        ),
                        policyWithoutDigest()
                )
        );
    }

    private DefaultSignatureGenerationService serviceWithKey(KeyPair keyPair) {
        KeyPair effectiveKeyPair;
        try {
            effectiveKeyPair = null == keyPair ? keyPair() : keyPair;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }

        return new DefaultSignatureGenerationService(
                new SignaturePolicyResolver(null, null),
                new SignatureKeyMaterialResolver(null, null, null) {
                    @Override
                    public Optional<ResolvedKeyMaterial> resolveForSigning(String keyId) {
                        return Optional.of(new ResolvedKeyMaterial(keyId, "pkcs12-key", effectiveKeyPair.getPublic(), effectiveKeyPair.getPrivate(), null));
                    }
                },
                new ContentDigestService(),
                new CanonicalizationService(new SignatureComponentResolver()),
                new SignatureComponentResolver(),
                new CryptoSigningService(new EcdsaP256SignatureCodec())
        );
    }

    private OutgoingHttpMessage message() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        return new OutgoingHttpMessage(
                "POST",
                URI.create("https://partner.example.com/v1/payments-instructions"),
                headers,
                "{\"paymentId\":\"p-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                null
        );
    }

    private ResolvedGenerationPolicy policyWithoutDigest() {
        return new ResolvedGenerationPolicy(
                "common-outbound",
                "sig",
                "agc",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                List.of(CoveredComponent.of("@method"), CoveredComponent.of("@path")),
                false,
                true,
                false,
                Duration.ofMinutes(5),
                false,
                24
        );
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
