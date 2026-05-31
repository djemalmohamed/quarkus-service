package com.service.infrastructure.signature.validation.service;

import com.service.infrastructure.signature.rfc.ContentDigestService;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.service.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.service.infrastructure.signature.validation.model.SignatureValidationCode;
import com.service.infrastructure.signature.validation.model.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DigestValidationServiceTest {

    private final ContentDigestService contentDigestService = new ContentDigestService();
    private final DigestValidationService service = new DigestValidationService(contentDigestService);

    @Test
    void shouldValidateMatchingDigest() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        String digestHeader = contentDigestService.buildDigestHeader(body, SignatureConstants.DIGEST_SHA_256);

        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, List.of(digestHeader)),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(true), "partner-key", "ecdsa-p256-sha256");

        assertNull(result);
    }

    @Test
    void shouldReturnExplicitFailureWhenDigestDoesNotMatchBody() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        String digestHeader = contentDigestService.buildDigestHeader("{\"paymentId\":\"p-2\"}".getBytes(StandardCharsets.UTF_8), SignatureConstants.DIGEST_SHA_256);

        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, List.of(digestHeader)),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(true), "partner-key", "ecdsa-p256-sha256");

        assertNotNull(result);
        assertEquals(SignatureValidationCode.SIG_INVALID_DIGEST, result.code());
    }

    @Test
    void shouldRejectUnsupportedDigestAlgorithm() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, List.of("sha-512=:dGVzdA==:")),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(true), "partner-key", "ecdsa-p256-sha256");

        assertNotNull(result);
        assertEquals(SignatureValidationCode.SIG_UNSUPPORTED_DIGEST, result.code());
    }

    @Test
    void shouldReturnMissingDigestWhenRequired() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(true), "partner-key", "ecdsa-p256-sha256");

        assertNotNull(result);
        assertEquals(SignatureValidationCode.SIG_MISSING_DIGEST, result.code());
    }

    @Test
    void shouldIgnoreMissingDigestWhenPolicyDoesNotRequireIt() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(false), "partner-key", "ecdsa-p256-sha256");

        assertNull(result);
    }

    @Test
    void shouldRejectMultipleContentDigestHeaderValues() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, List.of(
                        contentDigestService.buildDigestHeader(body, SignatureConstants.DIGEST_SHA_256),
                        contentDigestService.buildDigestHeader(body, SignatureConstants.DIGEST_SHA_256)
                )),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(true), "partner-key", "ecdsa-p256-sha256");

        assertNotNull(result);
        assertEquals(SignatureValidationCode.SIG_INVALID_DIGEST, result.code());
    }

    @Test
    void shouldRejectMultipleContentDigestEntriesInsideSingleHeaderValue() {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        String digestHeader = contentDigestService.buildDigestHeader(body, SignatureConstants.DIGEST_SHA_256);
        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "localhost",
                Map.of(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, List.of(digestHeader + ", " + digestHeader)),
                body,
                null
        );

        ValidationResult result = service.validate(message, policy(true), "partner-key", "ecdsa-p256-sha256");

        assertNotNull(result);
        assertEquals(SignatureValidationCode.SIG_INVALID_DIGEST, result.code());
    }

    private ResolvedValidationPolicy policy(boolean requireDigest) {
        return new ResolvedValidationPolicy(
                "common",
                false,
                true,
                List.of(SignatureConstants.HEADER_SIGNATURE, SignatureConstants.HEADER_SIGNATURE_INPUT, SignatureConstants.HEADER_CONTENT_DIGEST),
                List.of(
                        CoveredComponent.of(SignatureConstants.COMPONENT_METHOD),
                        CoveredComponent.of(SignatureConstants.COMPONENT_PATH),
                        CoveredComponent.of(SignatureConstants.HEADER_CONTENT_DIGEST)
                ),
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                requireDigest,
                true,
                false,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                false,
                "NONE",
                true
        );
    }
}
