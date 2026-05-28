package com.service.infrastructure.signature.generation.service;

import com.service.infrastructure.signature.rfc.EcdsaP256SignatureCodec;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.service.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.security.Signature;

/**
 * Performs the cryptographic signing step for outbound HTTP Message Signatures.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CryptoSigningService {

    private final EcdsaP256SignatureCodec ecdsaP256SignatureCodec;

    /**
     * Signs the canonical signature base with the configured asymmetric signing material.
     *
     * @param algorithm logical HTTP Message Signatures algorithm
     * @param signatureBase canonicalized signature base bytes
     * @param keyMaterial resolved signing material that must contain a private key
     * @return raw wire-format signature bytes ready for the {@code Signature} header
     */
    public byte[] sign(String algorithm, byte[] signatureBase, ResolvedKeyMaterial keyMaterial) {
        if (!SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256.equals(SignatureConstants.normalize(algorithm))) {
            throw new SignatureConfigurationException("Unsupported signature algorithm: " + algorithm);
        }
        return signSignature(signatureBase, keyMaterial);
    }

    /**
     * Executes the concrete JCA signing call for the supported ECDSA P-256 algorithm.
     *
     * @param signatureBase canonicalized signature base bytes
     * @param keyMaterial resolved signing material
     * @return raw wire-format signature bytes
     */
    private byte[] signSignature(byte[] signatureBase, ResolvedKeyMaterial keyMaterial) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            if (keyMaterial.privateKey() == null) {
                throw new SignatureConfigurationException("Missing private key for signing");
            }
            signature.initSign(keyMaterial.privateKey());
            signature.update(signatureBase);
            return ecdsaP256SignatureCodec.toWireSignature(signature.sign());
        } catch (Exception e) {
            throw new SignatureInfrastructureException("Unable to sign message", e);
        }
    }
}
