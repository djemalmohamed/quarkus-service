package com.playground.gateway.infrastructure.signature.rfc;

import com.playground.gateway.infrastructure.signature.rfc.model.CoveredComponent;
import com.playground.gateway.infrastructure.signature.rfc.model.ParsedSignatureInput;
import com.playground.gateway.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.playground.gateway.infrastructure.signature.rfc.model.SignatureMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
    public byte[] buildSignatureBase(SignatureMessage message, ParsedSignatureInput signatureInput) {
        return buildSignatureBase(SignatureContextMessage.standalone(message), signatureInput);
    }

    /**
     * Reconstructs the inbound signature base exactly as expected by the verifier.
     *
     * @param context RFC-facing signature context being validated
     * @param signatureInput parsed signature input containing the covered components
     * @return UTF-8 encoded signature base
     */
    public byte[] buildSignatureBase(SignatureContextMessage context, ParsedSignatureInput signatureInput) {
        return buildSignatureBaseData(context, signatureInput).signatureBase();
    }

    /**
     * Resolves the covered components and returns both the canonical base and the resolved values.
     *
     * @param context RFC-facing signature context being validated
     * @param signatureInput parsed signature input containing the covered components
     * @return resolved signature-base data
     */
    public SignatureBaseData buildSignatureBaseData(
            SignatureContextMessage context,
            ParsedSignatureInput signatureInput
    ) {
        return buildSignatureBaseData(context, signatureInput.components(), signatureInput.signatureParams());
    }

    /**
     * Reconstructs the outbound signature base exactly as it will be signed.
     *
     * @param message RFC-facing message abstraction being signed
     * @param components concrete covered components
     * @param signatureParams serialized signature parameters
     * @return UTF-8 encoded signature base
     */
    public byte[] buildSignatureBase(SignatureMessage message, List<CoveredComponent> components, String signatureParams) {
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
    public byte[] buildSignatureBase(SignatureContextMessage context, List<CoveredComponent> components, String signatureParams) {
        return buildSignatureBaseData(context, components, signatureParams).signatureBase();
    }

    /**
     * Resolves the covered components and returns both the canonical base and the resolved values.
     *
     * @param context RFC-facing signature context being signed
     * @param components concrete covered components
     * @param signatureParams serialized signature parameters
     * @return resolved signature-base data
     */
    public SignatureBaseData buildSignatureBaseData(
            SignatureContextMessage context,
            List<CoveredComponent> components,
            String signatureParams
    ) {
        List<String> lines = new ArrayList<>();
        Map<String, String> componentValues = new LinkedHashMap<>();
        for (CoveredComponent component : components) {
            String value = componentResolver.resolve(component, context);
            componentValues.put(component.identifier(), value);
            lines.add(component.canonicalIdentifier().toLowerCase(Locale.ROOT) + ": " + value);
        }
        lines.add("\"@signature-params\": " + signatureParams);
        return new SignatureBaseData(
                String.join("\n", lines).getBytes(StandardCharsets.UTF_8),
                componentValues
        );
    }

    /**
     * Canonical signature base plus the resolved component values used to build it.
     *
     * @param signatureBase UTF-8 encoded signature base
     * @param componentValues covered component identifiers associated with their resolved values
     */
    public record SignatureBaseData(byte[] signatureBase, Map<String, String> componentValues) {
        public SignatureBaseData {
            signatureBase = null == signatureBase ? new byte[0] : signatureBase.clone();
            componentValues = null == componentValues
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(componentValues));
        }

        @Override
        public byte[] signatureBase() {
            return signatureBase.clone();
        }
    }
}
