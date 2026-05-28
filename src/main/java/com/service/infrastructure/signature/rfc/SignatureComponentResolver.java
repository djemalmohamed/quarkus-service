package com.service.infrastructure.signature.rfc;

import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.rfc.model.SignatureMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves HTTP Message Signatures components from an RFC-facing message abstraction.
 */
@ApplicationScoped
public class SignatureComponentResolver {

    /**
     * Resolves one configured signature component against a generic RFC message.
     *
     * @param component configured component name, including derived components such as {@code @method}
     * @param message RFC-facing message from which the component value must be extracted
     * @return canonical string value to place in the signature base
     */
    public String resolve(CoveredComponent component, SignatureMessage message) {
        return resolve(component, SignatureContextMessage.standalone(message));
    }

    /**
     * Resolves one configured signature component against a full signature context.
     *
     * @param component configured component name, including derived components such as {@code @method}
     * @param context signature context exposing the current message plus the optional associated request
     * @return canonical string value to place in the signature base
     */
    public String resolve(CoveredComponent component, SignatureContextMessage context) {
        SignatureMessage source = sourceMessage(component, context);
        String normalized = component.name();
        return switch (normalized) {
            case SignatureConstants.COMPONENT_METHOD -> safe(source.method());
            case SignatureConstants.COMPONENT_PATH -> path(source.path());
            case SignatureConstants.COMPONENT_QUERY -> query(source.query());
            case SignatureConstants.COMPONENT_AUTHORITY -> safe(source.authority());
            case SignatureConstants.COMPONENT_STATUS -> source.status() == null ? "" : String.valueOf(source.status());
            case SignatureConstants.COMPONENT_TARGET_URI -> safe(source.targetUri());
            case SignatureConstants.HEADER_CONTENT_LENGTH -> contentLength(source);
            default -> {
                String header = source.firstHeader(component.name());
                if (header == null) {
                    throw new MissingCoveredComponentException(component.identifier());
                }
                yield header;
            }
        };
    }

    /**
     * Enriches the outbound header map with derived headers that may be required by signature policies.
     *
     * <p>For example, if {@code content-length} is configured as a signed component and no explicit
     * header is present, the method adds a computed {@code Content-Length} value based on the body.
     * All business correlation headers such as {@code Request-ID} and {@code Request-Timestamp}
     * must already be provided by the caller.</p>
     *
     * @param headers existing outbound headers
     * @param body outbound payload body
     * @param components configured components for the active generation policy
     * @return enriched header map ready to be used for canonicalization and request emission
     */
    public Map<String, String> enrichDerivedHeaders(Map<String, String> headers, byte[] body, Iterable<CoveredComponent> components) {
        Map<String, String> enriched = new LinkedHashMap<>(headers);
        for (CoveredComponent component : components) {
            String normalized = component.name();
            if (SignatureConstants.HEADER_CONTENT_LENGTH.equals(normalized)
                    && firstHeaderValue(enriched, SignatureConstants.HEADER_CONTENT_LENGTH) == null) {
                enriched.put(SignatureConstants.HTTP_HEADER_CONTENT_LENGTH, String.valueOf(body == null ? 0 : body.length));
            }
        }
        return enriched;
    }

    private SignatureMessage sourceMessage(CoveredComponent component, SignatureContextMessage context) {
        if (!component.requestComponent()) {
            return context == null ? null : context.currentMessage();
        }
        SignatureMessage associatedRequest = context == null ? null : context.associatedRequest();
        if (associatedRequest == null) {
            throw new MissingCoveredComponentException(component.identifier());
        }
        return associatedRequest;
    }

    private String contentLength(SignatureMessage message) {
        String header = message.firstHeader(SignatureConstants.HEADER_CONTENT_LENGTH);
        return header != null ? header : String.valueOf(message.bodyLength());
    }

    private String path(String path) {
        return path == null || path.isBlank() ? "/" : path;
    }

    private String query(String query) {
        return query == null ? "?" : "?" + query;
    }

    /**
     * Formats an optional query string for inclusion in a target URI.
     *
     * @param query raw query string
     * @return an empty string when the query is absent, otherwise the query prefixed with {@code ?}
     */
    private String querySuffix(String query) {
        return query == null || query.isBlank() ? "" : "?" + query;
    }

    /**
     * Normalizes nullable values to an empty string to keep canonicalization stable.
     *
     * @param value candidate value
     * @return original value or an empty string when the input is {@code null}
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Normalizes a component name for case-insensitive comparisons.
     *
     * @param component raw component name
     * @return lowercase representation, or an empty string when the input is {@code null}
     */
    private String firstHeaderValue(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        String normalizedName = SignatureConstants.normalize(name);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (SignatureConstants.normalize(entry.getKey()).equals(normalizedName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
