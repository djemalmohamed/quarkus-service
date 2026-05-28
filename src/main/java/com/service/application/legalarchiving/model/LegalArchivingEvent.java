package com.service.application.legalarchiving.model;

import java.util.List;

/**
 * Application representation of the legal-archiving message plus the technical context needed to emit it.
 *
 * @param requestId the correlation identifier of the archived HTTP interaction
 * @param operation the business operation name associated with the exchange
 * @param direction whether the exchange is inbound or outbound
 * @param phase whether the event represents a request or a response
 * @param payload the archived HTTP body bytes
 * @param signature the raw {@code Signature} header value when present
 * @param signatureInput the raw {@code Signature-Input} header value when present
 * @param signatureParameters the resolved covered components from {@code Signature-Input}
 */
public record LegalArchivingEvent(
        String requestId,
        String operation,
        String direction,
        String phase,
        byte[] payload,
        String signature,
        String signatureInput,
        List<SignatureParameter> signatureParameters) {

    /**
     * Creates an immutable application event ready to be archived.
     */
    public LegalArchivingEvent {
        payload = null == payload ? new byte[0] : payload.clone();
        signatureParameters = null == signatureParameters ? List.of() : List.copyOf(signatureParameters);
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
     * @return {@code true} when signature data is present on the archived HTTP interaction
     */
    public boolean hasSignatureData() {
        return (null != signature && !signature.isBlank())
                || (null != signatureInput && !signatureInput.isBlank());
    }

    /**
     * Name/value representation of a covered HTTP signature component.
     *
     * @param key the component identifier, for example {@code content-digest} or {@code @method}
     * @param value the resolved value of that component
     */
    public record SignatureParameter(String key, String value) {
    }
}
