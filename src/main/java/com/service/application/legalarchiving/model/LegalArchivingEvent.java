package com.service.application.legalarchiving.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Application representation of one legal-archiving message.
 *
 * @param eventId the identifier of the archived event
 * @param operation the business operation associated with the event
 * @param direction whether the event is inbound or outbound
 * @param phase whether the event represents an input or an output phase
 * @param http the archived HTTP context associated with the exchange
 * @param payload the message payload bytes to archive
 * @param signature the signature field value when present
 * @param signatureInput the signature-input field value when present
 * @param signatureComponents the resolved signature components associated with the event
 */
public record LegalArchivingEvent(
        String eventId,
        String operation,
        String direction,
        String phase,
        HttpContext http,
        byte[] payload,
        String signature,
        String signatureInput,
        List<SignatureComponent> signatureComponents) {

    /**
     * Creates an immutable application event ready to be archived.
     */
    public LegalArchivingEvent {
        http = null == http ? HttpContext.empty() : http;
        payload = null == payload ? new byte[0] : payload.clone();
        signatureComponents = null == signatureComponents ? List.of() : List.copyOf(signatureComponents);
    }

    /**
     * Convenience constructor kept so call sites can still pass the currently archived HTTP fields directly.
     *
     * @param eventId the identifier of the archived event
     * @param operation the business operation associated with the event
     * @param direction whether the event is inbound or outbound
     * @param phase whether the event represents an input or an output phase
     * @param httpMethod the archived HTTP method associated with the exchange
     * @param httpPath the archived HTTP path associated with the exchange
     * @param payload the message payload bytes to archive
     * @param signature the signature field value when present
     * @param signatureInput the signature-input field value when present
     * @param signatureComponents the resolved signature components associated with the event
     */
    public LegalArchivingEvent(
            String eventId,
            String operation,
            String direction,
            String phase,
            String httpMethod,
            String httpPath,
            byte[] payload,
            String signature,
            String signatureInput,
            List<SignatureComponent> signatureComponents) {
        this(
                eventId,
                operation,
                direction,
                phase,
                new HttpContext(httpMethod, httpPath),
                payload,
                signature,
                signatureInput,
                signatureComponents
        );
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * @return the archived HTTP method when available
     */
    public String httpMethod() {
        return http.method();
    }

    /**
     * @return the archived HTTP path when available
     */
    public String httpPath() {
        return http.path();
    }

    /**
     * @return {@code true} when a payload body is available for archiving
     */
    public boolean hasPayload() {
        return payload.length > 0;
    }

    /**
     * @return {@code true} when signature data is present on the archived event
     */
    public boolean hasSignatureData() {
        return (null != signature && !signature.isBlank())
                || (null != signatureInput && !signatureInput.isBlank());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LegalArchivingEvent that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(operation, that.operation)
                && Objects.equals(direction, that.direction)
                && Objects.equals(phase, that.phase)
                && Objects.equals(http, that.http)
                && Arrays.equals(payload, that.payload)
                && Objects.equals(signature, that.signature)
                && Objects.equals(signatureInput, that.signatureInput)
                && Objects.equals(signatureComponents, that.signatureComponents);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(eventId, operation, direction, phase, http, signature, signatureInput, signatureComponents);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "LegalArchivingEvent{"
                + "eventId='" + eventId + '\''
                + ", operation='" + operation + '\''
                + ", direction='" + direction + '\''
                + ", phase='" + phase + '\''
                + ", http=" + http
                + ", payloadLength=" + payload.length
                + ", signature='" + signature + '\''
                + ", signatureInput='" + signatureInput + '\''
                + ", signatureComponents=" + signatureComponents
                + '}';
    }

    /**
     * Archived HTTP fields associated with the exchange.
     *
     * <p>The legal-archiving event keeps those fields grouped so future HTTP metadata can be added
     * without continuously growing the top-level event record.</p>
     *
     * @param method the archived HTTP method
     * @param path the archived HTTP path
     */
    public record HttpContext(String method, String path) {

        /**
         * @return an empty HTTP context used when no HTTP metadata is available
         */
        public static HttpContext empty() {
            return new HttpContext(null, null);
        }
    }

    /**
     * Name/value representation of one resolved signature component.
     *
     * @param key the component identifier
     * @param value the resolved value of that component
     */
    public record SignatureComponent(String key, String value) {
    }
}
