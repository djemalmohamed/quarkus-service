package com.service.application.legalarchiving.model;

import java.util.List;

/**
 * Application representation of one legal-archiving message.
 *
 * @param eventId the identifier of the archived event
 * @param operation the business operation associated with the event
 * @param direction whether the event is inbound or outbound
 * @param phase whether the event represents an input or an output phase
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

    /**
     * Name/value representation of one resolved signature component.
     *
     * @param key the component identifier
     * @param value the resolved value of that component
     */
    public record SignatureComponent(String key, String value) {
    }
}
