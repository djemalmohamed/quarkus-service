package com.service.infrastructure.signature.generation.service;

import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.service.infrastructure.signature.generation.model.SignatureGenerationResult;
import com.service.infrastructure.signature.rfc.model.SignatureContextMessage;

/**
 * Signs outbound HTTP messages according to a resolved generation policy.
 */
public interface SignatureGenerationService {

    /**
     * Signs the given outbound message using either an explicit endpoint rule or the configured
     * auto-resolution logic.
     *
     * @param message outbound HTTP message to sign
     * @param targetName optional logical target name
     * @param requestedPolicy explicit generation rule name, or {@code null} to auto-resolve it
     * @return generated signature metadata and transport headers
     */
    SignatureGenerationResult sign(OutgoingHttpMessage message, String targetName, String requestedPolicy);

    /**
     * Signs the given outbound signature context after resolving the matching generation policy.
     *
     * @param context outbound signature context containing the current message and an optional
     *                associated request for {@code ;req} components
     * @param targetName optional logical target name
     * @param requestedPolicy explicit generation rule name, or {@code null} to auto-resolve it
     * @return generated signature metadata and transport headers
     */
    SignatureGenerationResult sign(
            SignatureContextMessage context,
            String targetName,
            String requestedPolicy
    );

    /**
     * Signs the given outbound message with an already resolved generation policy.
     *
     * @param message outbound HTTP message to sign
     * @param policy generation policy already resolved by the caller
     * @return generated signature metadata and transport headers
     */
    SignatureGenerationResult sign(OutgoingHttpMessage message, ResolvedGenerationPolicy policy);

    /**
     * Signs the given outbound signature context with an already resolved generation policy.
     *
     * @param context outbound signature context containing the current message and an optional
     *                associated request for {@code ;req} components
     * @param policy generation policy already resolved by the caller
     * @return generated signature metadata and transport headers
     */
    SignatureGenerationResult sign(
            SignatureContextMessage context,
            ResolvedGenerationPolicy policy
    );
}
