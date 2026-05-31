package com.service.infrastructure.signature.shared.service;

import com.service.infrastructure.signature.config.SignatureConfig;
import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.service.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignaturePolicyResolverTest {

    @Test
    void shouldResolveCommonValidationPolicyForPaymentsAndSpecificDeansOverride() {
        SignaturePolicyResolver resolver = new SignaturePolicyResolver(signatureConfig(), fieldMapper());
        resolver.init();

        ResolvedValidationPolicy paymentPolicy = resolver.resolveInboundValidation(
                new IncomingHttpMessage("POST", "/v1/payments-instructions", null, "localhost", Map.of(), new byte[0], null),
                null
        );
        ResolvedValidationPolicy deansPolicy = resolver.resolveInboundValidation(
                new IncomingHttpMessage("POST", "/v1/deans-bulks", null, "localhost", Map.of(), new byte[0], null),
                null
        );

        assertEquals("common", paymentPolicy.name());
        assertTrue(paymentPolicy.requiredComponents().contains(CoveredComponent.of(SignatureConstants.COMPONENT_METHOD)));
        assertEquals("deans-inbound", deansPolicy.name());
        assertTrue(deansPolicy.requiredComponents().stream().noneMatch(component -> CoveredComponent.of(SignatureConstants.COMPONENT_METHOD).equals(component)));
    }

    @Test
    void shouldResolveExplicitOutboundValidationPolicyByName() {
        SignaturePolicyResolver resolver = new SignaturePolicyResolver(signatureConfig(), fieldMapper());
        resolver.init();

        ResolvedValidationPolicy responsePolicy = resolver.resolveOutboundValidation(
                new IncomingHttpMessage("POST", "/any-path", null, "localhost", Map.of(), new byte[0], 200),
                "payment-provider-response"
        );

        assertEquals("payment-provider-response", responsePolicy.name());
        assertTrue(responsePolicy.requiredComponents().contains(CoveredComponent.of(SignatureConstants.COMPONENT_STATUS)));
        assertFalse(responsePolicy.requiredHeaders().contains("status"));
        assertFalse(responsePolicy.requiredHeaders().contains(SignatureConstants.HEADER_REQUEST_ID));
    }

    @Test
    void shouldResolveInboundResponseGenerationPolicy() {
        SignaturePolicyResolver resolver = new SignaturePolicyResolver(signatureConfig(), fieldMapper());
        resolver.init();

        Optional<ResolvedGenerationPolicy> responsePolicy = resolver.resolveInboundGeneration(
                null,
                new OutgoingHttpMessage("POST", URI.create("https://gateway.example.com/v1/payments-instructions"), Map.of(), new byte[0], 200)
        );

        assertTrue(responsePolicy.isPresent());
        assertEquals("common", responsePolicy.get().name());
        assertTrue(responsePolicy.get().components().contains(CoveredComponent.of(SignatureConstants.COMPONENT_STATUS)));
        assertTrue(responsePolicy.get().components().contains(CoveredComponent.request(SignatureConstants.HEADER_SIGNATURE)));
    }

    @Test
    void shouldResolveCommonOutboundPolicyForPaymentTargetAndSpecificDeansOverride() {
        SignaturePolicyResolver resolver = new SignaturePolicyResolver(signatureConfig(), fieldMapper());
        resolver.init();

        Optional<ResolvedGenerationPolicy> paymentPolicy = resolver.resolveOutboundGeneration(
                null,
                "payment-provider",
                new OutgoingHttpMessage("POST", URI.create("https://partner.example.com/payments"), Map.of(), new byte[0], null)
        );
        Optional<ResolvedGenerationPolicy> deansPolicy = resolver.resolveOutboundGeneration(
                null,
                null,
                new OutgoingHttpMessage("POST", URI.create("https://partner.example.com/v1/deans-bulks"), Map.of(), new byte[0], null)
        );

        assertTrue(paymentPolicy.isPresent());
        assertEquals("common-outbound", paymentPolicy.get().name());
        assertTrue(paymentPolicy.get().components().contains(CoveredComponent.of(SignatureConstants.COMPONENT_METHOD)));

        assertTrue(deansPolicy.isPresent());
        assertEquals("deans-outbound", deansPolicy.get().name());
        assertTrue(deansPolicy.get().components().stream().noneMatch(component -> CoveredComponent.of(SignatureConstants.COMPONENT_METHOD).equals(component)));
    }

    private SignatureFieldMapper fieldMapper() {
        return new SignatureFieldMapper();
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
            public DirectionConfig inbound() {
                return new DirectionConfig() {
                    @Override
                    public ValidationConfig validation() {
                        return () -> Map.of(
                                "common", validationEndpoint(SignatureConstants.MATCHER_ALL, List.of("header:method", "header:path", "header:content-digest")),
                                "deans-inbound", validationEndpoint("POST /v1/deans-bulks", List.of("header:path", "header:content-digest"))
                        );
                    }

                    @Override
                    public GenerationConfig generation() {
                        return () -> Map.of(
                                "common", generationEndpoint(
                                        SignatureConstants.MATCHER_ALL,
                                        Optional.empty(),
                                        List.of("header:content-digest", "header:response-timestamp", "@status", "signature;req", "request-id;req")
                                )
                        );
                    }
                };
            }

            @Override
            public DirectionConfig outbound() {
                return new DirectionConfig() {
                    @Override
                    public ValidationConfig validation() {
                        return () -> Map.of(
                                "payment-provider-response", validationEndpoint(
                                        "GET /provider-response",
                                        List.of("header:content-digest", "header:response-timestamp", "@status", "signature;req", "request-id;req")
                                )
                        );
                    }

                    @Override
                    public GenerationConfig generation() {
                        return () -> Map.of(
                                "common-outbound", generationEndpoint(SignatureConstants.MATCHER_ALL, Optional.of("payment-provider"), List.of("header:method", "header:path", "header:content-digest")),
                                "deans-outbound", generationEndpoint("POST /v1/deans-bulks", Optional.empty(), List.of("header:path", "header:content-digest"))
                        );
                    }
                };
            }

            @Override
            public RevocationConfig revocation() {
                return () -> false;
            }
        };
    }

    private SignatureConfig.ValidationEndpointConfig validationEndpoint(String matcher, List<String> fields) {
        return new SignatureConfig.ValidationEndpointConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String algorithm() {
                return SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256;
            }

            @Override
            public String digestAlgorithm() {
                return SignatureConstants.DIGEST_SHA_256;
            }

            @Override
            public boolean failFast() {
                return false;
            }

            @Override
            public List<String> fields() {
                return fields;
            }

            @Override
            public boolean requireCreated() {
                return true;
            }

            @Override
            public boolean requireExpires() {
                return false;
            }

            @Override
            public boolean requireNonce() {
                return false;
            }

            @Override
            public String nonceCheckMode() {
                return "NONE";
            }

            @Override
            public Duration maxSignatureAge() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Duration clockSkew() {
                return Duration.ofMinutes(1);
            }

            @Override
            public boolean rejectOnFailure() {
                return true;
            }

            @Override
            public String matcher() {
                return matcher;
            }
        };
    }

    private SignatureConfig.GenerationEndpointConfig generationEndpoint(String matcher, Optional<String> targetName, List<String> fields) {
        return new SignatureConfig.GenerationEndpointConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String matcher() {
                return matcher;
            }

            @Override
            public Optional<String> targetName() {
                return targetName;
            }

            @Override
            public String signatureLabel() {
                return "sig";
            }

            @Override
            public String keyId() {
                return "gateway-key";
            }

            @Override
            public String algorithm() {
                return SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256;
            }

            @Override
            public String digestAlgorithm() {
                return SignatureConstants.DIGEST_SHA_256;
            }

            @Override
            public List<String> fields() {
                return fields;
            }

            @Override
            public boolean includeCreated() {
                return true;
            }

            @Override
            public boolean includeExpires() {
                return false;
            }

            @Override
            public Duration expiresIn() {
                return Duration.ofMinutes(5);
            }

            @Override
            public boolean includeNonce() {
                return false;
            }

            @Override
            public int nonceLength() {
                return 24;
            }
        };
    }
}
