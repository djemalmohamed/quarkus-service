package com.example.legalarchive.infrastructure.adapters.legalarchiving;

import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.application.model.LegalArchivingEvent.SignatureParameter;
import com.example.legalarchive.infrastructure.signature.model.SignatureData;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

/**
 * Maps already prepared payload and signature data to the application legal-archiving event.
 */
@ApplicationScoped
public class LegalArchivingEventMapper {

    /**
     * Builds one legal-archiving event from infrastructure data already prepared upstream.
     *
     * @param requestId the request identifier propagated with the archived exchange
     * @param operation the derived business operation name
     * @param direction whether the archived exchange is inbound or outbound
     * @param phase whether the event represents the request or the response
     * @param payload the archived payload bytes when present
     * @param signatureData the signature data already prepared by the signature layer
     * @return the application event ready for the legal-archiving use case
     */
    public LegalArchivingEvent toEvent(
            String requestId,
            String operation,
            String direction,
            String phase,
            byte[] payload,
            SignatureData signatureData) {
        return new LegalArchivingEvent(
                requestId,
                operation,
                direction,
                phase,
                payload,
                null == signatureData ? null : signatureData.signature(),
                null == signatureData ? null : signatureData.signatureInput(),
                toSignatureParameters(signatureData));
    }

    /**
     * Converts prepared signature component values to the list expected by the legal-archiving event.
     *
     * @param signatureData the already prepared signature data
     * @return the signature parameters ready for archiving
     */
    private List<SignatureParameter> toSignatureParameters(SignatureData signatureData) {
        if (null == signatureData) {
            return List.of();
        }
        return signatureData.componentValues().entrySet().stream()
                .map(this::toSignatureParameter)
                .toList();
    }

    /**
     * Maps one prepared signature component entry to the application signature-parameter structure.
     *
     * @param entry the prepared signature component entry
     * @return the application signature parameter
     */
    private SignatureParameter toSignatureParameter(Map.Entry<String, String> entry) {
        return new SignatureParameter(entry.getKey(), entry.getValue());
    }
}
