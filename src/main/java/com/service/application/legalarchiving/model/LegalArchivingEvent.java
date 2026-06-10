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
 * @param httpMethod the archived HTTP method associated with the exchange
 * @param httpPath the archived HTTP path associated with the exchange
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
        String httpMethod,
        String httpPath,
        byte[] payload,
        String signature,
        String signatureInput,
        List<SignatureComponent> signatureComponents) {

    /**
     * Creates an immutable application event ready to be archived.
     */
    public LegalArchivingEvent {
        payload = null == payload ? new byte[0] : payload.clone();
        signatureComponents = null == signatureComponents ? List.of() : List.copyOf(signatureComponents);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
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
                && Objects.equals(httpMethod, that.httpMethod)
                && Objects.equals(httpPath, that.httpPath)
                && Arrays.equals(payload, that.payload)
                && Objects.equals(signature, that.signature)
                && Objects.equals(signatureInput, that.signatureInput)
                && Objects.equals(signatureComponents, that.signatureComponents);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(eventId, operation, direction, phase, httpMethod, httpPath, signature, signatureInput, signatureComponents);
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
                + ", httpMethod='" + httpMethod + '\''
                + ", httpPath='" + httpPath + '\''
                + ", payloadLength=" + payload.length
                + ", signature='" + signature + '\''
                + ", signatureInput='" + signatureInput + '\''
                + ", signatureComponents=" + signatureComponents
                + '}';
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
