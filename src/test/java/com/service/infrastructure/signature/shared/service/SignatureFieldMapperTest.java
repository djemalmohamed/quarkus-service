package com.service.infrastructure.signature.shared.service;

import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignatureFieldMapperTest {

    @Test
    void shouldDeriveRequiredHeadersFromConfiguredFields() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();

        List<String> headers = mapper.requiredHeaders(List.of(
                "header:method",
                "header:requesting-agent",
                "header:request-id",
                "header:request-timestamp",
                "header:content-digest"
        ));

        assertEquals(
                List.of(
                        SignatureConstants.HEADER_SIGNATURE_INPUT,
                        SignatureConstants.HEADER_SIGNATURE,
                        "requesting-agent",
                        SignatureConstants.HEADER_REQUEST_ID,
                        SignatureConstants.HEADER_REQUEST_TIMESTAMP,
                        SignatureConstants.HEADER_CONTENT_DIGEST
                ),
                headers);
    }

    @Test
    void shouldExpandConfiguredGenerationFieldsIntoConcreteComponents() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();

        OutgoingHttpMessage message = new OutgoingHttpMessage(
                "POST",
                URI.create("https://partner.example.com/v1/payments-instructions?test=true"),
                Map.of(
                        "Content-Type", "application/json",
                        "Requesting-Agent", "gateway",
                        "Request-ID", "e8a1ab35-2f4f-4bf9-9b86-19799690f531",
                        "Request-Timestamp", "2026-04-09T15:00:00Z"
                ),
                "{\"payment\":{\"amount\":\"10.00\"}}".getBytes(StandardCharsets.UTF_8),
                null
        );

        List<CoveredComponent> components = mapper.generationComponents(
                List.of(
                        "header:content-digest",
                        "header:requesting-agent",
                        "header:request-id",
                        "header:request-timestamp",
                        "header:method",
                        "header:path"
                ),
                message
        );

        assertEquals(
                List.of(
                        CoveredComponent.of(SignatureConstants.HEADER_CONTENT_DIGEST),
                        CoveredComponent.of("requesting-agent"),
                        CoveredComponent.of(SignatureConstants.HEADER_REQUEST_ID),
                        CoveredComponent.of(SignatureConstants.HEADER_REQUEST_TIMESTAMP),
                        CoveredComponent.of(SignatureConstants.COMPONENT_METHOD),
                        CoveredComponent.of(SignatureConstants.COMPONENT_PATH)
                ),
                components
        );
    }

    @Test
    void shouldSupportRequestBoundResponseFields() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();

        List<String> headers = mapper.requiredHeaders(List.of(
                "header:content-digest",
                "header:response-timestamp",
                "@status",
                "signature;req",
                "request-id;req"
        ));

        assertEquals(
                List.of(
                        SignatureConstants.HEADER_SIGNATURE_INPUT,
                        SignatureConstants.HEADER_SIGNATURE,
                        SignatureConstants.HEADER_CONTENT_DIGEST,
                        SignatureConstants.HEADER_RESPONSE_TIMESTAMP
                ),
                headers
        );

        assertEquals(
                List.of(
                        CoveredComponent.of(SignatureConstants.HEADER_CONTENT_DIGEST),
                        CoveredComponent.of(SignatureConstants.HEADER_RESPONSE_TIMESTAMP),
                        CoveredComponent.of(SignatureConstants.COMPONENT_STATUS),
                        CoveredComponent.request(SignatureConstants.HEADER_SIGNATURE),
                        CoveredComponent.request(SignatureConstants.HEADER_REQUEST_ID)
                ),
                mapper.generationComponents(
                        List.of(
                                "header:content-digest",
                                "header:response-timestamp",
                                "@status",
                                "signature;req",
                                "request-id;req"
                        ),
                        new OutgoingHttpMessage(
                                "POST",
                                URI.create("https://partner.example.com/v1/payments-instructions"),
                                Map.of(),
                                new byte[0],
                                200
                        )
                )
        );
    }

    @Test
    void shouldIgnoreBlankDerivedRequestAndWildcardFieldsWhenResolvingRequiredHeaders() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();
        List<String> configuredFields = new ArrayList<>();
        configuredFields.add(null);
        configuredFields.add("   ");
        configuredFields.add("@method");
        configuredFields.add("request-id;req");
        configuredFields.add("header:*");
        configuredFields.add("header:*;req");
        configuredFields.add("header:content-type");

        List<String> headers = mapper.requiredHeaders(configuredFields);

        assertEquals(
                List.of(
                        SignatureConstants.HEADER_SIGNATURE_INPUT,
                        SignatureConstants.HEADER_SIGNATURE,
                        "content-type"
                ),
                headers
        );
    }

    @Test
    void shouldExpandWildcardHeadersForValidationAndSkipSignatureHeaders() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("content-type", List.of("application/json"));
        headers.put("request-id", List.of("req-1"));
        headers.put("signature", List.of("sig=:abc:"));
        headers.put("signature-input", List.of("sig=(\"@method\")"));

        IncomingHttpMessage message = new IncomingHttpMessage(
                "POST",
                "/v1/payments-instructions",
                null,
                "partner.example.com",
                headers,
                new byte[0],
                200
        );

        List<CoveredComponent> components = mapper.validationComponents(
                List.of("header:*", "header:*;req"),
                message
        );

        assertEquals(
                List.of(
                        CoveredComponent.of("content-type"),
                        CoveredComponent.of(SignatureConstants.HEADER_REQUEST_ID)
                ),
                components
        );
    }

    @Test
    void shouldSkipWildcardExpansionWhenValidationHeadersAreMissing() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();

        List<CoveredComponent> components = mapper.validationComponents(
                List.of("header:*"),
                new IncomingHttpMessage(
                        "POST",
                        "/v1/payments-instructions",
                        null,
                        "partner.example.com",
                        null,
                        new byte[0],
                        200
                )
        );

        assertEquals(List.of(), components);
    }

    @Test
    void shouldSkipStatusComponentWhenStatusIsMissing() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();

        List<CoveredComponent> validationComponents = mapper.validationComponents(
                List.of("@status", "header:request-id"),
                new IncomingHttpMessage(
                        "POST",
                        "/v1/payments-instructions",
                        null,
                        "partner.example.com",
                        Map.of(SignatureConstants.HEADER_REQUEST_ID, List.of("req-1")),
                        new byte[0],
                        null
                )
        );

        List<CoveredComponent> generationComponents = mapper.generationComponents(
                List.of("@status", "header:request-id"),
                new OutgoingHttpMessage(
                        "POST",
                        URI.create("https://partner.example.com/v1/payments-instructions"),
                        Map.of(SignatureConstants.HTTP_HEADER_REQUEST_ID, "req-1"),
                        new byte[0],
                        null
                )
        );

        assertEquals(List.of(CoveredComponent.of(SignatureConstants.HEADER_REQUEST_ID)), validationComponents);
        assertEquals(List.of(CoveredComponent.of(SignatureConstants.HEADER_REQUEST_ID)), generationComponents);
    }

    @Test
    void shouldExpandWildcardHeadersForGenerationAndPreserveCurrentMessageScope() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();
        List<String> configuredFields = new ArrayList<>();
        configuredFields.add(null);
        configuredFields.add("header:*");
        configuredFields.add("header:*;req");
        configuredFields.add("   ");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Request-ID", "req-1");
        headers.put("Signature", "sig=:abc:");
        headers.put("Signature-Input", "sig=(\"@method\")");

        List<CoveredComponent> components = mapper.generationComponents(
                configuredFields,
                new OutgoingHttpMessage(
                        "POST",
                        URI.create("https://partner.example.com/v1/payments-instructions"),
                        headers,
                        new byte[0],
                        200
                )
        );

        assertEquals(
                List.of(
                        CoveredComponent.of("content-type"),
                        CoveredComponent.of(SignatureConstants.HEADER_REQUEST_ID)
                ),
                components
        );
    }

    @Test
    void shouldIgnoreNullWildcardHeaderEntriesDuringValidationExpansion() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(null, List.of("ignored"));
        headers.put("Request-ID", List.of("req-1"));

        List<CoveredComponent> components = mapper.validationComponents(
                List.of("header:*"),
                new IncomingHttpMessage(
                        "POST",
                        "/v1/payments-instructions",
                        null,
                        "partner.example.com",
                        headers,
                        new byte[0],
                        200
                )
        );

        assertEquals(List.of(CoveredComponent.of(SignatureConstants.HEADER_REQUEST_ID)), components);
    }

    @Test
    void shouldUseMergeFunctionWhenGenerationHeadersContainDuplicateNames() {
        SignatureFieldMapper mapper = new SignatureFieldMapper();
        Map<String, String> duplicateHeaders = new AbstractMap<>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                LinkedHashSet<Entry<String, String>> entries = new LinkedHashSet<>();
                entries.add(new SimpleEntry<>("X-Test", "first"));
                entries.add(new SimpleEntry<>("X-Test", "second"));
                return entries;
            }
        };

        List<CoveredComponent> components = mapper.generationComponents(
                List.of("header:*"),
                new OutgoingHttpMessage(
                        "POST",
                        URI.create("https://partner.example.com/v1/payments-instructions"),
                        duplicateHeaders,
                        new byte[0],
                        200
                )
        );

        assertEquals(List.of(CoveredComponent.of("x-test")), components);
    }
}
