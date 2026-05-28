package com.service.infrastructure.signature.validation.service;

import com.service.infrastructure.signature.config.SignatureConfig;
import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.service.CryptoSigningService;
import com.service.infrastructure.signature.rfc.CanonicalizationService;
import com.service.infrastructure.signature.rfc.ContentDigestService;
import com.service.infrastructure.signature.rfc.EcdsaP256SignatureCodec;
import com.service.infrastructure.signature.rfc.SignatureComponentResolver;
import com.service.infrastructure.signature.rfc.SignatureInputParser;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import com.service.infrastructure.signature.shared.service.SignatureKeyMaterialResolver;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.service.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.service.infrastructure.signature.validation.model.SignatureValidationCode;
import com.service.infrastructure.signature.validation.model.ValidationReport;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSignatureValidationEngineTest {

    private static final String REQUEST_ID = "req-123";
    private static final String REQUEST_TIMESTAMP = "2026-04-13T10:15:30Z";
    private static final long CREATED = 1710000000L;

    @Test
    void shouldReturnSignerDnForValidSignature() throws Exception {
        TestContext context = createContext(false, true);

        IncomingHttpMessage message = signedMessage(
                context,
                "psp1",
                "psp1",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                defaultComponents(),
                defaultHeaders(),
                defaultHeaders(),
                body(),
                "/v1/payments-instructions",
                false
        );

        ValidationReport report = context.engine.validate(message, policy());

        assertTrue(report.valid());
        assertEquals("psp1", report.keyId());
        assertTrue(report.signerDn().contains("CN=provider-http-signature"));
        assertNotNull(report.signatureData());
        assertEquals(REQUEST_ID, report.signatureData().componentValues().get("request-id"));
        assertEquals("POST", report.signatureData().componentValues().get("@method"));
        assertTrue(report.signatureData().signatureInput().startsWith("sig="));
        assertTrue(new String(report.signatureData().signature(), StandardCharsets.UTF_8).startsWith("sig=:"));
    }

    @Test
    void shouldRejectUnknownKeyId() throws Exception {
        TestContext context = createContext(false, true);

        IncomingHttpMessage message = signedMessage(
                context,
                "psp1",
                "unknown-partner",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                defaultComponents(),
                defaultHeaders(),
                defaultHeaders(),
                body(),
                "/v1/payments-instructions",
                false
        );

        ValidationReport report = context.engine.validate(message, policy());

        assertFalse(report.valid());
        assertEquals(SignatureValidationCode.SIG_UNKNOWN_KEY, report.issues().getFirst().code());
        assertNotNull(report.signatureData());
        assertEquals(REQUEST_ID, report.signatureData().componentValues().get("request-id"));
    }

    @Test
    void shouldRejectRevokedCertificate() throws Exception {
        TestContext context = createContext(true, true);

        IncomingHttpMessage message = signedMessage(
                context,
                "psp1",
                "psp1",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                defaultComponents(),
                defaultHeaders(),
                defaultHeaders(),
                body(),
                "/v1/payments-instructions",
                false
        );

        ValidationReport report = context.engine.validate(message, policy());

        assertFalse(report.valid());
        assertEquals(SignatureValidationCode.SIG_REVOKED_CERTIFICATE, report.issues().getFirst().code());
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        TestContext context = createContext(false, true);

        IncomingHttpMessage message = signedMessage(
                context,
                "psp1",
                "psp1",
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                defaultComponents(),
                defaultHeaders(),
                defaultHeaders(),
                body(),
                "/v1/payments-instructions",
                true
        );

        ValidationReport report = context.engine.validate(message, policy());

        assertFalse(report.valid());
        assertEquals(SignatureValidationCode.SIG_INVALID_SIGNATURE, report.issues().getFirst().code());
    }

    @Test
    void shouldValidateResponseSignatureUsingAssociatedRequestComponents() throws Exception {
        TestContext context = createContext(false, true);

        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("Request-ID", REQUEST_ID);
        requestHeaders.put("Requesting-Agent", "AGC");
        requestHeaders.put("Request-Timestamp", REQUEST_TIMESTAMP);
        requestHeaders.put("Signature", "sig=:request-signature:");

        byte[] responseBody = "{\"status\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
        List<CoveredComponent> responseComponents = List.of(
                CoveredComponent.of("content-digest"),
                CoveredComponent.of("content-length"),
                CoveredComponent.of("content-type"),
                CoveredComponent.of("response-timestamp"),
                CoveredComponent.of("@status"),
                CoveredComponent.request("signature"),
                CoveredComponent.request("request-id")
        );

        Map<String, String> signingHeaders = new LinkedHashMap<>();
        signingHeaders.put("Content-Type", "application/json");
        signingHeaders.put("Response-Timestamp", "2026-04-14T09:00:00Z");
        signingHeaders = context.componentResolver.enrichDerivedHeaders(signingHeaders, responseBody, responseComponents);
        signingHeaders.put(
                SignatureConstants.HTTP_HEADER_CONTENT_DIGEST,
                context.contentDigestService.buildDigestHeader(responseBody, SignatureConstants.DIGEST_SHA_256)
        );

        String signatureParams = signatureParams(responseComponents, "psp1", SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256);
        OutgoingHttpMessage relatedRequest = new OutgoingHttpMessage(
                "POST",
                URI.create("https://partner.example.com/v1/payments-instructions"),
                requestHeaders,
                body(),
                null
        );
        byte[] signatureBase = context.canonicalizationService.buildSignatureBase(
                SignatureContextMessage.withAssociatedRequest(
                        new OutgoingHttpMessage(
                                "POST",
                                URI.create("https://partner.example.com/v1/payments-instructions"),
                                signingHeaders,
                                responseBody,
                                200
                        ),
                        relatedRequest
                ),
                responseComponents,
                signatureParams
        ).signatureBase();
        byte[] signatureBytes = context.cryptoSigningService.sign(
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                signatureBase,
                context.keyMaterialResolver.resolveForSigning("psp1").orElseThrow()
        );

        Map<String, String> validationHeaders = new LinkedHashMap<>(signingHeaders);
        validationHeaders.put(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, "sig=" + signatureParams);
        validationHeaders.put(SignatureConstants.HTTP_HEADER_SIGNATURE, "sig=:" + Base64.getEncoder().encodeToString(signatureBytes) + ":");

        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "partner.example.com",
                toMultiValueMap(validationHeaders),
                responseBody,
                200
        );

        ValidationReport report = context.engine.validate(
                SignatureContextMessage.withAssociatedRequest(message, relatedRequest),
                responsePolicy()
        );

        assertTrue(report.valid());
        assertNotNull(report.signatureData());
        assertEquals("200", report.signatureData().componentValues().get("@status"));
        assertEquals("sig=:request-signature:", report.signatureData().componentValues().get("signature;req"));
    }

    private TestContext createContext(boolean revoked, boolean trusted) throws Exception {
        ContentDigestService contentDigestService = new ContentDigestService();
        SignatureComponentResolver componentResolver = new SignatureComponentResolver();
        CanonicalizationService canonicalizationService = new CanonicalizationService(componentResolver);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        X509Certificate certificate = new StubCertificate("CN=provider-http-signature,OU=Payments,O=DemoPSP,C=FR", keyPair.getPublic());

        SignatureKeyMaterialResolver keyMaterialResolver = new SignatureKeyMaterialResolver(null, null, null) {
            @Override
            public Optional<ResolvedKeyMaterial> resolveForSigning(String keyId) {
                if (!"psp1".equals(keyId)) {
                    return Optional.empty();
                }
                return Optional.of(new ResolvedKeyMaterial(keyId, "test-signing-key", keyPair.getPublic(), keyPair.getPrivate(), certificate));
            }

            @Override
            public Optional<ResolvedKeyMaterial> resolveForVerification(String keyId) {
                if (!"psp1".equals(keyId)) {
                    return Optional.empty();
                }
                return Optional.of(new ResolvedKeyMaterial(keyId, "test-certificate", keyPair.getPublic(), null, certificate));
            }
        };

        CryptoSigningService cryptoSigningService = new CryptoSigningService(new EcdsaP256SignatureCodec());
        CryptoVerificationService cryptoVerificationService = new CryptoVerificationService(new EcdsaP256SignatureCodec());
        DigestValidationService digestValidationService = new DigestValidationService(contentDigestService);

        DefaultSignatureValidationEngine engine = new DefaultSignatureValidationEngine(
                signatureConfig(),
                new SignatureInputParser(),
                digestValidationService,
                keyMaterialResolver,
                validationService(revoked, trusted),
                componentResolver,
                canonicalizationService,
                cryptoVerificationService
        );

        return new TestContext(
                contentDigestService,
                componentResolver,
                canonicalizationService,
                cryptoSigningService,
                keyMaterialResolver,
                engine
        );
    }

    private IncomingHttpMessage signedMessage(
            TestContext context,
            String signingKeyId,
            String advertisedKeyId,
            String advertisedAlgorithm,
            List<String> components,
            Map<String, String> headersForSigning,
            Map<String, String> headersForValidation,
            byte[] body,
            String path,
            boolean tamperSignature
    ) {
        List<CoveredComponent> coveredComponents = toCoveredComponents(components);
        Map<String, String> signingHeaders = context.componentResolver.enrichDerivedHeaders(headersForSigning, body, coveredComponents);
        signingHeaders.put(
                SignatureConstants.HTTP_HEADER_CONTENT_DIGEST,
                context.contentDigestService.buildDigestHeader(body, SignatureConstants.DIGEST_SHA_256)
        );

        String signatureParams = signatureParamsFromNames(components, advertisedKeyId, advertisedAlgorithm);
        byte[] signatureBase = context.canonicalizationService.buildSignatureBase(
                new OutgoingHttpMessage("POST", URI.create("https://partner.example.com" + path), signingHeaders, body, null),
                coveredComponents,
                signatureParams
        ).signatureBase();
        byte[] signatureBytes = context.cryptoSigningService.sign(
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                signatureBase,
                context.keyMaterialResolver.resolveForSigning(signingKeyId).orElseThrow()
        );
        if (tamperSignature) {
            signatureBytes[0] = (byte) (signatureBytes[0] ^ 0x01);
        }

        Map<String, String> validationHeaders = context.componentResolver.enrichDerivedHeaders(headersForValidation, body, coveredComponents);
        validationHeaders.put(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, signingHeaders.get(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST));
        validationHeaders.put(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, "sig=" + signatureParams);
        validationHeaders.put(SignatureConstants.HTTP_HEADER_SIGNATURE, "sig=:" + Base64.getEncoder().encodeToString(signatureBytes) + ":");

        return new IncomingHttpMessage("POST", path, null, "partner.example.com", toMultiValueMap(validationHeaders), body, null);
    }

    private String signatureParams(List<CoveredComponent> components, String keyId, String algorithm) {
        String coveredComponents = components.stream()
                .map(CoveredComponent::signatureInputToken)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return "(" + coveredComponents + ")"
                + ";created=" + CREATED
                + ";keyid=\"" + keyId + "\""
                + ";alg=\"" + algorithm + "\"";
    }

    private String signatureParamsFromNames(List<String> components, String keyId, String algorithm) {
        return signatureParams(toCoveredComponents(components), keyId, algorithm);
    }

    private Map<String, List<String>> toMultiValueMap(Map<String, String> headers) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        headers.forEach((name, value) -> values.put(name, List.of(value)));
        return values;
    }

    private ResolvedValidationPolicy policy() {
        return new ResolvedValidationPolicy(
                "common",
                false,
                true,
                List.of("signature-input", "signature", "content-digest", "content-length", "content-type", "requesting-agent", "request-id", "request-timestamp"),
                toCoveredComponents(defaultComponents()),
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                true,
                true,
                false,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                false,
                "NONE",
                true
        );
    }

    private ResolvedValidationPolicy responsePolicy() {
        return new ResolvedValidationPolicy(
                "payment-provider-response",
                false,
                true,
                List.of("signature-input", "signature", "content-digest", "content-length", "content-type", "response-timestamp"),
                List.of(
                        CoveredComponent.of("content-digest"),
                        CoveredComponent.of("content-length"),
                        CoveredComponent.of("content-type"),
                        CoveredComponent.of("response-timestamp"),
                        CoveredComponent.of("@status"),
                        CoveredComponent.request("signature"),
                        CoveredComponent.request("request-id")
                ),
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                true,
                true,
                false,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                false,
                "NONE",
                true
        );
    }

    private List<String> defaultComponents() {
        return List.of("content-digest", "content-length", "content-type", "requesting-agent", "request-id", "request-timestamp", "@method", "@path");
    }

    private List<CoveredComponent> toCoveredComponents(List<String> components) {
        return components.stream().map(CoveredComponent::fromConfiguredField).toList();
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Requesting-Agent", "PSP1");
        headers.put("Request-ID", REQUEST_ID);
        headers.put("Request-Timestamp", REQUEST_TIMESTAMP);
        return headers;
    }

    private byte[] body() {
        return "{\"paymentId\":\"p-100\",\"amount\":\"42.00\"}".getBytes(StandardCharsets.UTF_8);
    }

    private SignatureConfig signatureConfig() {
        return new SignatureConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean logValidationDetails() {
                return false;
            }

            @Override
            public RevocationConfig revocation() {
                return () -> false;
            }

            @Override
            public DirectionConfig inbound() {
                return directionConfig();
            }

            @Override
            public DirectionConfig outbound() {
                return directionConfig();
            }
        };
    }

    private SignatureConfig.DirectionConfig directionConfig() {
        return new SignatureConfig.DirectionConfig() {
            @Override
            public SignatureConfig.ValidationConfig validation() {
                return () -> Map.of();
            }

            @Override
            public SignatureConfig.GenerationConfig generation() {
                return () -> Map.of();
            }
        };
    }

    private CertificateRevocationValidationService validationService(boolean revoked, boolean trusted) {
        return new CertificateRevocationValidationService(null, null, null) {
            @Override
            public boolean isTrusted(X509Certificate certificate) {
                return trusted;
            }

            @Override
            public boolean isRevoked(X509Certificate certificate) {
                return revoked;
            }
        };
    }

    private record TestContext(
            ContentDigestService contentDigestService,
            SignatureComponentResolver componentResolver,
            CanonicalizationService canonicalizationService,
            CryptoSigningService cryptoSigningService,
            SignatureKeyMaterialResolver keyMaterialResolver,
            DefaultSignatureValidationEngine engine
    ) {
    }

    private static final class StubCertificate extends X509Certificate {

        private final X500Principal subject;
        private final PublicKey publicKey;

        private StubCertificate(String subject, PublicKey publicKey) {
            this.subject = new X500Principal(subject);
            this.publicKey = publicKey;
        }

        @Override
        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
        }

        @Override
        public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return subject;
        }

        @Override
        public Principal getSubjectDN() {
            return subject;
        }

        @Override
        public Date getNotBefore() {
            return new Date(0);
        }

        @Override
        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "SHA256withECDSA";
        }

        @Override
        public String getSigAlgOID() {
            return "1.2.840.10045.4.3.2";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) throws CertificateException {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException {
        }

        @Override
        public String toString() {
            return subject.getName();
        }

        @Override
        public PublicKey getPublicKey() {
            return publicKey;
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return subject;
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return subject;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public List<String> getExtendedKeyUsage() throws CertificateParsingException {
            return null;
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
            return null;
        }

        @Override
        public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
            return null;
        }
    }
}
