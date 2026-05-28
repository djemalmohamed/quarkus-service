package com.service.infrastructure.signature.validation.rest;

import com.service.infrastructure.adapters.legalarchiving.LegalArchivingRequestHandler;
import com.service.infrastructure.signature.SignatureContextKeys;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import com.service.infrastructure.signature.validation.error.SignatureValidationException;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.service.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import com.service.infrastructure.signature.validation.model.SignatureData;
import com.service.infrastructure.signature.validation.model.ValidationIssue;
import com.service.infrastructure.signature.validation.model.ValidationReport;
import com.service.infrastructure.signature.validation.model.SignatureValidationCode;
import com.service.infrastructure.signature.validation.service.SignatureValidationEngine;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SignatureValidationFilterTest {

    @Test
    void shouldSkipInternalPathWithoutResolvingPolicy() throws Exception {
        AtomicInteger resolveCalls = new AtomicInteger();
        SignatureValidationEngine engine = noopValidationEngine();
        LegalArchivingRequestHandler legalArchivingRequestHandler = mock(LegalArchivingRequestHandler.class);
        SignatureValidationFilter filter = new SignatureValidationFilter(
                new SignaturePolicyResolver(null, null) {
                    @Override
                    public Optional<ResolvedValidationPolicy> resolveInboundValidationOptional(IncomingHttpMessage message) {
                        resolveCalls.incrementAndGet();
                        return Optional.empty();
                    }
                },
                engine,
                legalArchivingRequestHandler
        );

        Response response = filter.filter(requestContext("POST", "https://gateway.example.com/q/health", Map.of(), new byte[0]).context());

        assertNull(response);
        assertEquals(0, resolveCalls.get());
        verify(legalArchivingRequestHandler, never()).handle(any(), any());
    }

    @Test
    void shouldReturnNullWhenNoPolicyMatches() throws Exception {
        AtomicInteger validateCalls = new AtomicInteger();
        LegalArchivingRequestHandler legalArchivingRequestHandler = mock(LegalArchivingRequestHandler.class);
        SignatureValidationFilter filter = new SignatureValidationFilter(
                new SignaturePolicyResolver(null, null) {
                    @Override
                    public Optional<ResolvedValidationPolicy> resolveInboundValidationOptional(IncomingHttpMessage message) {
                        return Optional.empty();
                    }
                },
                new SignatureValidationEngine() {
                    @Override
                    public ValidationReport validate(IncomingHttpMessage message, ResolvedValidationPolicy policy) {
                        validateCalls.incrementAndGet();
                        return ValidationReport.ok(policy.name(), "COLLECT_ALL", null, null, null, Map.of());
                    }

                    @Override
                    public ValidationReport validate(SignatureContextMessage context, ResolvedValidationPolicy policy) {
                        throw new UnsupportedOperationException();
                    }
                },
                legalArchivingRequestHandler
        );

        Response response = filter.filter(requestContext(
                "POST",
                "https://gateway.example.com/v1/payments-instructions",
                Map.of("Request-Id", List.of("req-1")),
                "body".getBytes(StandardCharsets.UTF_8)
        ).context());

        assertNull(response);
        assertEquals(0, validateCalls.get());
        verify(legalArchivingRequestHandler, never()).handle(any(), any());
    }

    @Test
    void shouldValidateRestoreBodyAndExposeSignatureDataWhenPolicyRequiresIt() throws Exception {
        byte[] body = "{\"paymentId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8);
        RequestContextStub requestContext = requestContext(
                "POST",
                "https://gateway.example.com/v1/payments-instructions?step=1",
                Map.of("Request-Id", List.of("req-1"), "Signature", List.of("sig=:abc:")),
                body
        );
        AtomicReference<IncomingHttpMessage> validatedMessage = new AtomicReference<>();
        LegalArchivingRequestHandler legalArchivingRequestHandler = mock(LegalArchivingRequestHandler.class);

        SignatureValidationFilter filter = new SignatureValidationFilter(
                new SignaturePolicyResolver(null, null) {
                    @Override
                    public Optional<ResolvedValidationPolicy> resolveInboundValidationOptional(IncomingHttpMessage message) {
                        return Optional.of(policy(true, false));
                    }
                },
                new SignatureValidationEngine() {
                    @Override
                    public ValidationReport validate(IncomingHttpMessage message, ResolvedValidationPolicy policy) {
                        validatedMessage.set(message);
                        return ValidationReport.ok(
                                policy.name(),
                                "COLLECT_ALL",
                                "psp1",
                                null,
                                policy.signatureAlgorithm(),
                                new SignatureData(
                                        "sig=:abc:".getBytes(StandardCharsets.UTF_8),
                                        "sig=(\"@method\")",
                                        Map.of("@method", "POST")
                                ),
                                Map.of()
                        );
                    }

                    @Override
                    public ValidationReport validate(SignatureContextMessage context, ResolvedValidationPolicy policy) {
                        throw new UnsupportedOperationException();
                    }
                },
                legalArchivingRequestHandler
        );

        Response response = filter.filter(requestContext.context());

        assertNull(response);
        ValidationReport report = (ValidationReport) requestContext.context().getProperty(SignatureValidationRequestContextKeys.VALIDATION_REPORT);
        assertEquals("psp1", report.keyId());
        assertArrayEquals(body, validatedMessage.get().body());
        assertEquals("req-1", validatedMessage.get().firstHeader("request-id"));
        assertArrayEquals(body, requestContext.currentBody());
        assertEquals(
                "sig=(\"@method\")",
                ((SignatureData) requestContext.context().getProperty(SignatureContextKeys.REQUEST_SIGNATURE_DATA)).signatureInput()
        );
        verify(legalArchivingRequestHandler).handle(eq(requestContext.context()), eq(body));
    }

    @Test
    void shouldThrowWhenValidationFailsAndPolicyRejectsOnFailure() throws Exception {
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        RequestContextStub requestContext = requestContext(
                "POST",
                "https://gateway.example.com/v1/payments-instructions",
                Map.of("Request-Id", List.of("req-1")),
                body
        );
        LegalArchivingRequestHandler legalArchivingRequestHandler = mock(LegalArchivingRequestHandler.class);

        SignatureValidationFilter filter = new SignatureValidationFilter(
                new SignaturePolicyResolver(null, null) {
                    @Override
                    public Optional<ResolvedValidationPolicy> resolveInboundValidationOptional(IncomingHttpMessage message) {
                        return Optional.of(policy(true, true));
                    }
                },
                new SignatureValidationEngine() {
                    @Override
                    public ValidationReport validate(IncomingHttpMessage message, ResolvedValidationPolicy policy) {
                        return ValidationReport.ko(
                                policy.name(),
                                "FAIL_FAST",
                                null,
                                null,
                                null,
                                List.of(new ValidationIssue(SignatureValidationCode.SIG_INVALID_SIGNATURE, "invalid", "signature")),
                                Map.of()
                        );
                    }

                    @Override
                    public ValidationReport validate(SignatureContextMessage context, ResolvedValidationPolicy policy) {
                        throw new UnsupportedOperationException();
                    }
                },
                legalArchivingRequestHandler
        );

        assertThrows(SignatureValidationException.class, () -> filter.filter(requestContext.context()));
        verify(legalArchivingRequestHandler).handle(eq(requestContext.context()), eq(body));
    }

    private SignatureValidationEngine noopValidationEngine() {
        return new SignatureValidationEngine() {
            @Override
            public ValidationReport validate(IncomingHttpMessage message, ResolvedValidationPolicy policy) {
                return ValidationReport.ok(policy.name(), "COLLECT_ALL", null, null, null, Map.of());
            }

            @Override
            public ValidationReport validate(SignatureContextMessage context, ResolvedValidationPolicy policy) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ResolvedValidationPolicy policy(boolean bodyRequired, boolean rejectOnFailure) {
        return new ResolvedValidationPolicy(
                "common",
                false,
                bodyRequired,
                List.of(SignatureConstants.HEADER_REQUEST_ID),
                List.of(CoveredComponent.of(SignatureConstants.COMPONENT_METHOD)),
                SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256,
                SignatureConstants.DIGEST_SHA_256,
                false,
                false,
                false,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                false,
                "NONE",
                rejectOnFailure
        );
    }

    private RequestContextStub requestContext(String method, String uri, Map<String, List<String>> headers, byte[] body) {
        return new RequestContextStub(method, URI.create(uri), headers, body);
    }

    private static final class RequestContextStub {
        private final String method;
        private final URI uri;
        private final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final ContainerRequestContext context;
        private InputStream entityStream;

        private RequestContextStub(String method, URI uri, Map<String, List<String>> headers, byte[] body) {
            this.method = method;
            this.uri = uri;
            headers.forEach(this.headers::put);
            this.entityStream = new ByteArrayInputStream(body);
            this.context = (ContainerRequestContext) Proxy.newProxyInstance(
                    ContainerRequestContext.class.getClassLoader(),
                    new Class[]{ContainerRequestContext.class},
                    (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                        case "getMethod" -> method;
                        case "getHeaders" -> this.headers;
                        case "getUriInfo" -> uriInfo(uri);
                        case "getEntityStream" -> entityStream;
                        case "setEntityStream" -> {
                            entityStream = (InputStream) args[0];
                            yield null;
                        }
                        case "getProperty" -> properties.get((String) args[0]);
                        case "setProperty" -> {
                            properties.put((String) args[0], args[1]);
                            yield null;
                        }
                        default -> defaultValue(invokedMethod.getReturnType());
                    }
            );
        }

        private ContainerRequestContext context() {
            return context;
        }

        private byte[] currentBody() {
            try {
                return entityStream.readAllBytes();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static UriInfo uriInfo(URI uri) {
        return (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class[]{UriInfo.class},
                (proxy, invokedMethod, args) -> {
                    if ("getRequestUri".equals(invokedMethod.getName())) {
                        return uri;
                    }
                    return defaultValue(invokedMethod.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (double.class.equals(returnType)) {
            return 0d;
        }
        if (float.class.equals(returnType)) {
            return 0f;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (char.class.equals(returnType)) {
            return (char) 0;
        }
        return null;
    }
}
