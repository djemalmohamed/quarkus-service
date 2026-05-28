package com.playground.gateway.infrastructure.signature.validation.service;

import com.playground.gateway.infrastructure.signature.rfc.EcdsaP256SignatureCodec;
import com.playground.gateway.infrastructure.signature.rfc.model.ParsedSignature;
import com.playground.gateway.infrastructure.signature.rfc.model.ParsedSignatureInput;
import com.playground.gateway.infrastructure.signature.shared.SignatureConstants;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.playground.gateway.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.security.Signature;

/**
 * Performs the cryptographic verification step of HTTP Message Signatures.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CryptoVerificationService {

    private final EcdsaP256SignatureCodec ecdsaP256SignatureCodec;

    /**
     * Verifies the received HTTP Message Signature with the resolved verification material.
     *
     * @param signatureInput parsed {@code Signature-Input} metadata
     * @param signature parsed {@code Signature} value
     * @param signatureBase canonicalized signature base bytes
     * @param keyMaterial resolved verification material that must contain a public key
     * @return {@code true} when the signature is cryptographically valid
     */
    public boolean verify(ParsedSignatureInput signatureInput, ParsedSignature signature, byte[] signatureBase, ResolvedKeyMaterial keyMaterial) {
        String algorithm = SignatureConstants.normalize(signatureInput.algorithm());
        if (!SignatureConstants.ALGORITHM_ECDSA_P256_SHA_256.equals(algorithm)) {
            throw new SignatureConfigurationException("Unsupported signature algorithm: " + signatureInput.algorithm());
        }
        return verifySignature(signature, signatureBase, keyMaterial);
    }

    /**
     * Executes the concrete JCA verification call for the supported ECDSA P-256 algorithm.
     *
     * @param signature parsed signature bytes as received on the wire
     * @param signatureBase canonicalized signature base bytes
     * @param keyMaterial resolved verification material
     * @return {@code true} when the signature bytes verify successfully
     */
    private boolean verifySignature(ParsedSignature signature, byte[] signatureBase, ResolvedKeyMaterial keyMaterial) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(keyMaterial.publicKey());
            verifier.update(signatureBase);
            return verifier.verify(ecdsaP256SignatureCodec.toDerSignature(signature.signatureBytes()));
        } catch (SignatureInfrastructureException error) {
            return false;
        } catch (Exception e) {
            throw new SignatureInfrastructureException("Unable to verify signature", e);
        }
    }
}
