package com.service.infrastructure.signature.generation.service;

import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.rfc.CanonicalizationService;
import com.service.infrastructure.signature.rfc.CanonicalizationResult;
import com.service.infrastructure.signature.rfc.ContentDigestService;
import com.service.infrastructure.signature.rfc.SignatureComponentResolver;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import com.service.infrastructure.signature.shared.service.SignatureKeyMaterialResolver;
import com.service.infrastructure.signature.shared.service.SignaturePolicyResolver;
import com.service.infrastructure.signature.validation.model.SignatureData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates HTTP Message Signatures and the corresponding transport headers for outbound messages.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultSignatureGenerationService implements SignatureGenerationService {

    private static final char[] NONCE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final List<String> DEFAULT_SIGNATURE_PARAMETER_ORDER = List.of("created", "expires", "keyid", "alg", "nonce");

    private final SignaturePolicyResolver policyResolver;

    private final SignatureKeyMaterialResolver keyMaterialResolver;

    private final ContentDigestService contentDigestService;

    private final CanonicalizationService canonicalizationService;

    private final SignatureComponentResolver componentResolver;

    private final CryptoSigningService cryptoSigningService;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Signs an outbound HTTP message after resolving the matching outbound generation policy.
     *
     * @param message outbound HTTP message to sign
     * @param targetName optional logical target name used during policy resolution
     * @param requestedPolicy explicit policy name, or {@code null} to auto-resolve one
     * @return generated signature metadata, or a neutral {@code none} result when no policy applies
     */
    @Override
    public SignatureGenerationResult sign(OutgoingHttpMessage message, String targetName, String requestedPolicy) {
        return sign(SignatureContextMessage.standalone(message), targetName, requestedPolicy);
    }

    /**
     * Signs an outbound signature context after resolving the matching outbound generation policy.
     *
     * @param context outbound signature context
     * @param targetName optional logical target name used during policy resolution
     * @param requestedPolicy explicit policy name, or {@code null} to auto-resolve one
     * @return generated signature metadata, or a neutral {@code none} result when no policy applies
     */
    @Override
    public SignatureGenerationResult sign(
            SignatureContextMessage context,
            String targetName,
            String requestedPolicy
    ) {
        OutgoingHttpMessage message = requireOutgoingMessage(context);
        Optional<ResolvedGenerationPolicy> resolvedPolicy = policyResolver.resolveOutboundGeneration(requestedPolicy, targetName, message);
        if (resolvedPolicy.isEmpty()) {
            return new SignatureGenerationResult("none", Map.of());
        }

        return sign(context, resolvedPolicy.get());
    }

    /**
     * Signs an outbound HTTP message with an already resolved generation policy.
     *
     * @param message outbound HTTP message to sign
     * @param policy resolved generation policy chosen by the caller
     * @return generated signature metadata ready to be added to the transport message
     */
    @Override
    public SignatureGenerationResult sign(OutgoingHttpMessage message, ResolvedGenerationPolicy policy) {
        return sign(SignatureContextMessage.standalone(message), policy);
    }

    /**
     * Signs an outbound signature context with an already resolved generation policy.
     *
     * @param context outbound signature context
     * @param policy resolved generation policy chosen by the caller
     * @return generated signature metadata ready to be added to the transport message
     */
    @Override
    public SignatureGenerationResult sign(
            SignatureContextMessage context,
            ResolvedGenerationPolicy policy
    ) {
        OutgoingHttpMessage message = requireOutgoingMessage(context);
        ResolvedKeyMaterial keyMaterial = keyMaterialResolver.resolveForSigning(policy.keyId())
                .orElseThrow(() -> new SignatureConfigurationException("Unknown signing keyId: " + policy.keyId()));

        Map<String, String> headers = componentResolver.enrichDerivedHeaders(message.headers(), message.body(), policy.components());
        if (policy.includeDigest()) {
            headers.put(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, contentDigestService.buildDigestHeader(message.body(), policy.digestAlgorithm()));
        }

        String signatureParams = buildSignatureParams(policy);
        CanonicalizationResult canonicalizationResult = canonicalizationService.buildSignatureBase(
                signatureContext(context, headers),
                policy.components(),
                signatureParams
        );
        byte[] signature = cryptoSigningService.sign(
                policy.signatureAlgorithm(),
                canonicalizationResult.signatureBase(),
                keyMaterial
        );

        String signatureInputHeader = policy.signatureLabel() + "=" + signatureParams;
        String signatureHeader = policy.signatureLabel() + "=:" + Base64.getEncoder().encodeToString(signature) + ":";

        Map<String, String> generatedHeaders = new LinkedHashMap<>();
        if (policy.includeDigest()) {
            generatedHeaders.put(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST, headers.get(SignatureConstants.HTTP_HEADER_CONTENT_DIGEST));
        }
        generatedHeaders.put(SignatureConstants.HTTP_HEADER_SIGNATURE_INPUT, signatureInputHeader);
        generatedHeaders.put(SignatureConstants.HTTP_HEADER_SIGNATURE, signatureHeader);

        return new SignatureGenerationResult(
                policy.name(),
                generatedHeaders,
                new SignatureData(
                        signatureHeader,
                        signatureInputHeader,
                        canonicalizationResult.componentValues())
        );
    }

    /**
     * Builds the RFC-facing signature context used during canonicalization.
     *
     * @param context outbound signature context being signed
     * @param headers effective headers after digest and derived-header enrichment
     * @return signature context used by the RFC layer
     */
    private SignatureContextMessage signatureContext(
            SignatureContextMessage context,
            Map<String, String> headers
    ) {
        OutgoingHttpMessage message = requireOutgoingMessage(context);
        OutgoingHttpMessage currentMessage = new OutgoingHttpMessage(
                message.method(),
                message.uri(),
                headers,
                message.body(),
                message.status()
        );
        return context.withCurrentMessage(currentMessage);
    }

    /**
     * Ensures that the current message carried by the signature context is an outbound message.
     *
     * @param context outbound signature context
     * @return current outbound message
     */
    private OutgoingHttpMessage requireOutgoingMessage(SignatureContextMessage context) {
        if (context.currentMessage() instanceof OutgoingHttpMessage message) {
            return message;
        }
        throw new IllegalArgumentException("Signature context current message must be an OutgoingHttpMessage");
    }

    /**
     * Builds the serialized {@code Signature-Input} parameters for one outbound message.
     *
     * <p>The method preserves the configured component order because it must match the exact order
     * used later during canonicalization and remote verification.</p>
     *
     * @param policy resolved generation policy
     * @return serialized signature parameters ready to be emitted on the wire
     */
    private String buildSignatureParams(ResolvedGenerationPolicy policy) {
        StringBuilder builder = new StringBuilder();
        builder.append(policy.components().stream()
                .map(CoveredComponent::signatureInputToken)
                .collect(Collectors.joining(" ", "(", ")")));

        long now = Instant.now().getEpochSecond();
        Map<String, String> parameterValues = new LinkedHashMap<>();
        if (policy.includeCreated()) {
            parameterValues.put("created", String.valueOf(now));
        }
        if (policy.includeExpires()) {
            parameterValues.put("expires", String.valueOf(now + policy.expiresIn().getSeconds()));
        }
        parameterValues.put("keyid", "\"" + policy.keyId() + "\"");
        parameterValues.put("alg", "\"" + policy.signatureAlgorithm() + "\"");
        if (policy.includeNonce()) {
            parameterValues.put("nonce", "\"" + randomNonce(policy.nonceLength()) + "\"");
        }

        DEFAULT_SIGNATURE_PARAMETER_ORDER.stream()
                .filter(parameterValues::containsKey)
                .forEach(parameterName -> builder.append(";").append(parameterName).append("=").append(parameterValues.get(parameterName)));
        return builder.toString();
    }

    /**
     * Generates a simple alphanumeric nonce for outbound signature parameters.
     *
     * @param size configured nonce length
     * @return random nonce with a minimum length of eight characters
     */
    private String randomNonce(int size) {
        int length = Math.max(size, 8);
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(NONCE_CHARS[secureRandom.nextInt(NONCE_CHARS.length)]);
        }
        return builder.toString();
    }

}
