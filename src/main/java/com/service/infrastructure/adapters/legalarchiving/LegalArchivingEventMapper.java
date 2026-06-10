package com.service.infrastructure.adapters.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.legalarchiving.model.LegalArchivingEvent.SignatureComponent;
import com.service.infrastructure.signature.validation.model.SignatureData;
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
     * @param httpMethod the archived HTTP method
     * @param httpPath the archived HTTP path
     * @param payload the archived payload bytes when present
     * @param signatureData the signature data already prepared by the signature layer
     * @return the application event ready for the legal-archiving use case
     */
    public LegalArchivingEvent toEvent(
            String eventId,
            String operation,
            String direction,
            String phase,
            String httpMethod,
            String httpPath,
            byte[] payload,
            SignatureData signatureData) {
        return new LegalArchivingEvent(
                eventId,
                operation,
                direction,
                phase,
                httpMethod,
                httpPath,
                payload,
                null == signatureData ? null : signatureData.signature(),
                null == signatureData ? null : signatureData.signatureInput(),
                toSignatureComponents(signatureData));
    }

    /**
     * Converts prepared signature component values to the list expected by the legal-archiving event.
     *
     * @param signatureData the already prepared signature data
     * @return the signature parameters ready for archiving
     */
    private List<SignatureComponent> toSignatureComponents(SignatureData signatureData) {
        if (null == signatureData) {
            return List.of();
        }
        return signatureData.componentValues().entrySet().stream()
                .map(this::toSignatureComponent)
                .toList();
    }

    /**
     * Maps one prepared signature component entry to the application signature-parameter structure.
     *
     * @param entry the prepared signature component entry
     * @return the application signature parameter
     */
    private SignatureComponent toSignatureComponent(Map.Entry<String, String> entry) {
        return new SignatureComponent(entry.getKey(), entry.getValue());
    }
}
