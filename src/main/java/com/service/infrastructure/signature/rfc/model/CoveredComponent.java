package com.service.infrastructure.signature.rfc.model;

import com.service.infrastructure.signature.shared.SignatureConstants;

/**
 * One HTTP Message Signatures covered component.
 *
 * <p>The current implementation keeps the model intentionally narrow and only supports the
 * RFC component form required by this project: a component name plus the optional {@code ;req}
 * flag used to resolve the value from the associated request when signing or validating a
 * response.</p>
 */
public record CoveredComponent(
        String name,
        boolean requestComponent
) {

    private static final String CONFIG_HEADER_PREFIX = "header:";
    private static final String CONFIG_REQ_SUFFIX = ";req";

    public CoveredComponent {
        name = normalizeName(name);
    }

    /**
     * Builds a current-message component from a raw identifier.
     *
     * @param name header or derived component name
     * @return normalized covered component
     */
    public static CoveredComponent of(String name) {
        return new CoveredComponent(name, false);
    }

    /**
     * Builds a request-associated component from a raw identifier.
     *
     * @param name header or derived component name
     * @return normalized request-associated covered component
     */
    public static CoveredComponent request(String name) {
        return new CoveredComponent(name, true);
    }

    /**
     * Parses one configured field entry into the RFC-facing component form.
     *
     * <p>The configuration accepts both the existing project notation such as
     * {@code header:request-id} and direct RFC-like identifiers such as {@code request-id;req}
     * or {@code @status}.</p>
     *
     * @param configuredField raw field entry from configuration
     * @return parsed component, or {@code null} when the field is blank
     */
    public static CoveredComponent fromConfiguredField(String configuredField) {
        String normalized = SignatureConstants.normalize(configuredField);
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.startsWith(CONFIG_HEADER_PREFIX)) {
            normalized = normalized.substring(CONFIG_HEADER_PREFIX.length());
        }

        boolean requestComponent = normalized.endsWith(CONFIG_REQ_SUFFIX);
        if (requestComponent) {
            normalized = normalized.substring(0, normalized.length() - CONFIG_REQ_SUFFIX.length());
        }

        normalized = switch (normalized) {
            case "method" -> SignatureConstants.COMPONENT_METHOD;
            case "path" -> SignatureConstants.COMPONENT_PATH;
            case "query" -> SignatureConstants.COMPONENT_QUERY;
            case "authority" -> SignatureConstants.COMPONENT_AUTHORITY;
            case "status" -> SignatureConstants.COMPONENT_STATUS;
            case "target-uri" -> SignatureConstants.COMPONENT_TARGET_URI;
            default -> normalized;
        };

        return new CoveredComponent(normalized, requestComponent);
    }

    /**
     * Indicates whether this component is one of the RFC derived identifiers such as
     * {@code @method} or {@code @status}.
     *
     * @return {@code true} when the component is derived instead of being a regular header
     */
    public boolean derived() {
        return name.startsWith("@");
    }

    /**
     * Returns the unquoted normalized identifier used by policy checks and diagnostics.
     *
     * @return identifier such as {@code content-digest} or {@code request-id;req}
     */
    public String identifier() {
        return requestComponent ? name + CONFIG_REQ_SUFFIX : name;
    }

    /**
     * Returns the RFC token form used inside {@code Signature-Input}.
     *
     * @return serialized covered component token
     */
    public String signatureInputToken() {
        return "\"" + name + "\"" + (requestComponent ? CONFIG_REQ_SUFFIX : "");
    }

    /**
     * Returns the canonical component identifier used on each line of the signature base.
     *
     * @return canonical signature base identifier
     */
    public String canonicalIdentifier() {
        return signatureInputToken();
    }

    private static String normalizeName(String name) {
        return SignatureConstants.normalize(name);
    }
}
