package com.playground.gateway.infrastructure.signature.rfc;

import com.playground.gateway.infrastructure.signature.shared.SignatureConstants;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.playground.gateway.infrastructure.signature.shared.service.HeaderUtils;
import com.playground.gateway.infrastructure.signature.rfc.model.CoveredComponent;
import com.playground.gateway.infrastructure.signature.rfc.model.ParsedSignature;
import com.playground.gateway.infrastructure.signature.rfc.model.ParsedSignatureInput;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the transport headers used by HTTP Message Signatures validation.
 *
 * <p>The parser converts raw header strings into structured model objects consumed by the
 * validation engine. It deliberately stays small and strict: malformed inputs are rejected early
 * so the engine can report a precise validation issue instead of continuing with ambiguous state.</p>
 */
@ApplicationScoped
public class SignatureInputParser {

    /**
     * Parses the {@code Signature-Input} header into its structured form.
     *
     * @param headers inbound transport headers
     * @return parsed signature input definition
     * @throws SignatureInfrastructureException when the header is missing or malformed
     */
    public ParsedSignatureInput parseSignatureInput(Map<String, List<String>> headers) {
        List<String> headerValues = HeaderUtils.matchingHeaderValues(headers, SignatureConstants.HEADER_SIGNATURE_INPUT);
        if (headerValues.isEmpty()) {
            throw new SignatureInfrastructureException("Missing Signature-Input header");
        }
        if (headerValues.size() > 1) {
            throw new SignatureInfrastructureException("Multiple Signature-Input header values are not supported");
        }

        String rawHeader = headerValues.get(0);
        if (rawHeader == null || rawHeader.isBlank()) {
            throw new SignatureInfrastructureException("Missing Signature-Input header");
        }
        if (StructuredFieldSupport.hasTopLevelComma(rawHeader)) {
            throw new SignatureInfrastructureException("Multiple Signature-Input entries are not supported");
        }

        StructuredFieldCursor cursor = new StructuredFieldCursor(rawHeader, SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT);
        cursor.skipOptionalWhitespace();
        String label = cursor.readToken("signature label");
        cursor.skipOptionalWhitespace();
        cursor.expect('=', "after signature label");
        cursor.skipOptionalWhitespace();
        int signatureParamsStart = cursor.position();
        List<CoveredComponent> components = parseCoveredComponents(cursor);

        String algorithm = null;
        String keyId = null;
        Long created = null;
        Long expires = null;
        String nonce = null;

        while (true) {
            cursor.skipOptionalWhitespace();
            if (!cursor.consumeIf(';')) {
                break;
            }
            cursor.skipOptionalWhitespace();
            String name = cursor.readToken("signature parameter").toLowerCase(Locale.ROOT);
            cursor.skipOptionalWhitespace();
            cursor.expect('=', "after parameter " + name);
            String value = cursor.readParameterValue(name);

            switch (name) {
                case "alg" -> algorithm = value;
                case "keyid" -> keyId = value;
                case "created" -> created = cursor.readLong("created", value);
                case "expires" -> expires = cursor.readLong("expires", value);
                case "nonce" -> nonce = value;
                default -> {
                }
            }
        }

        cursor.expectEnd();
        String signatureParams = rawHeader.substring(signatureParamsStart).trim();
        return new ParsedSignatureInput(label, components, signatureParams, algorithm, keyId, created, expires, nonce);
    }

    /**
     * Parses the {@code Signature} header and validates that it uses the expected label.
     *
     * @param headers inbound transport headers
     * @param label label previously extracted from {@code Signature-Input}
     * @return parsed signature bytes
     * @throws SignatureInfrastructureException when the header is missing, malformed or inconsistent
     */
    public ParsedSignature parseSignature(Map<String, List<String>> headers, String label) {
        List<String> headerValues = HeaderUtils.matchingHeaderValues(headers, SignatureConstants.HEADER_SIGNATURE);
        if (headerValues.isEmpty()) {
            throw new SignatureInfrastructureException("Missing Signature header");
        }
        if (headerValues.size() > 1) {
            throw new SignatureInfrastructureException("Multiple Signature header values are not supported");
        }

        String rawHeader = headerValues.get(0);
        if (rawHeader == null || rawHeader.isBlank()) {
            throw new SignatureInfrastructureException("Missing Signature header");
        }
        if (StructuredFieldSupport.hasTopLevelComma(rawHeader)) {
            throw new SignatureInfrastructureException("Multiple Signature entries are not supported");
        }

        StructuredFieldCursor cursor = new StructuredFieldCursor(rawHeader, SignatureConstants.HTTP_HEADER_SIGNATURE);
        cursor.skipOptionalWhitespace();
        String actualLabel = cursor.readToken("signature label");
        cursor.skipOptionalWhitespace();
        cursor.expect('=', "after signature label");
        if (label != null && !label.equals(actualLabel)) {
            throw new SignatureInfrastructureException("Signature label mismatch");
        }

        cursor.skipOptionalWhitespace();
        byte[] signatureBytes = cursor.readByteSequence("Signature value");
        cursor.expectEnd();
        return new ParsedSignature(actualLabel, signatureBytes);
    }

    /**
     * Parses the list of quoted covered components from the middle section of {@code Signature-Input}.
     *
     * <p>The grammar expected here is intentionally narrow because downstream canonicalization
     * depends on exact component ordering. Rejecting malformed tokens early prevents the engine
     * from reconstructing a signature base that does not correspond to the sender intent.</p>
     *
     * @param raw raw component list contained between parentheses
     * @return ordered list of covered components
     * @throws SignatureInfrastructureException when the component list is malformed
     */
    private List<CoveredComponent> parseCoveredComponents(StructuredFieldCursor cursor) {
        List<CoveredComponent> values = new ArrayList<>();
        cursor.expect('(', "to start covered components");
        while (true) {
            cursor.skipOptionalWhitespace();
            if (cursor.consumeIf(')')) {
                return values;
            }

            String componentName = cursor.readQuotedString("covered component");
            boolean requestComponent = false;
            while (true) {
                cursor.skipOptionalWhitespace();
                if (!cursor.consumeIf(';')) {
                    break;
                }
                cursor.skipOptionalWhitespace();
                String parameter = cursor.readToken("covered component parameter").toLowerCase(Locale.ROOT);
                if (!"req".equals(parameter)) {
                    throw new SignatureInfrastructureException("Unsupported covered component parameter: " + parameter + " at index " + cursor.position());
                }
                requestComponent = true;
            }
            values.add(new CoveredComponent(componentName, requestComponent));
            cursor.skipOptionalWhitespace();
            if (cursor.consumeIf(')')) {
                return values;
            }

            if (cursor.hasMore() && cursor.current() != '"') {
                throw new SignatureInfrastructureException("Invalid Signature-Input components");
            }
        }
    }
}
