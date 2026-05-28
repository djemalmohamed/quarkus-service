package com.service.infrastructure.signature.rfc;

import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.ParsedSignatureInput;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.rfc.model.SignatureMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the canonical signature base used by HTTP Message Signatures validation and generation.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CanonicalizationService {

    private final SignatureComponentResolver componentResolver;

    /**
     * Reconstructs the inbound signature base exactly as expected by the verifier.
     *
     * @param message RFC-facing message abstraction being validated
     * @param signatureInput parsed signature input containing the covered components
     * @return UTF-8 encoded signature base
     */
    public CanonicalizationResult buildSignatureBase(SignatureMessage message, ParsedSignatureInput signatureInput) {
        return buildSignatureBase(SignatureContextMessage.standalone(message), signatureInput);
    }

    /**
     * Reconstructs the inbound signature base exactly as expected by the verifier.
     *
     * @param context RFC-facing signature context being validated
     * @param signatureInput parsed signature input containing the covered components
     * @return UTF-8 encoded signature base
     */
    public CanonicalizationResult buildSignatureBase(SignatureContextMessage context, ParsedSignatureInput signatureInput) {
        return buildSignatureBase(context, signatureInput.components(), signatureInput.signatureParams());
    }

    /**
     * Reconstructs the outbound signature base exactly as it will be signed.
     *
     * @param message RFC-facing message abstraction being signed
     * @param components concrete covered components
     * @param signatureParams serialized signature parameters
     * @return UTF-8 encoded signature base
     */
    public CanonicalizationResult buildSignatureBase(SignatureMessage message, List<CoveredComponent> components, String signatureParams) {
        return buildSignatureBase(SignatureContextMessage.standalone(message), components, signatureParams);
    }

    /**
     * Reconstructs the outbound signature base exactly as it will be signed.
     *
     * @param context RFC-facing signature context being signed
     * @param components concrete covered components
     * @param signatureParams serialized signature parameters
     * @return UTF-8 encoded signature base
     */
    public CanonicalizationResult buildSignatureBase(SignatureContextMessage context, List<CoveredComponent> components, String signatureParams) {
        List<String> lines = new ArrayList<>();
        Map<String, String> componentValues = new LinkedHashMap<>();
        for (CoveredComponent component : components) {
            String value = componentResolver.resolve(component, context);
            lines.add(component.canonicalIdentifier().toLowerCase(Locale.ROOT) + ": " + value);
            componentValues.put(component.identifier(), value);
        }
        lines.add("\"@signature-params\": " + signatureParams);
        return new CanonicalizationResult(
                String.join("\n", lines).getBytes(StandardCharsets.UTF_8),
                componentValues
        );
    }
}
