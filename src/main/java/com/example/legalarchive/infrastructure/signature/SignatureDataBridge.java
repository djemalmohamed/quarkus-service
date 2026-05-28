package com.example.legalarchive.infrastructure.signature;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Bridges the signature module models used by the gateway code and the local legal-archiving
 * adapter model reused from the scaffold.
 */
@ApplicationScoped
public class SignatureDataBridge {

    /**
     * Converts the signature-validation model produced by the signature project to the local
     * legal-archiving signature model expected by the legal-archiving mapper.
     *
     * @param signatureData signature data produced by validation or generation
     * @return equivalent legal-archiving signature data, or {@code null} when absent
     */
    public com.example.legalarchive.infrastructure.signature.model.SignatureData toLegalArchivingSignatureData(
            com.playground.gateway.infrastructure.signature.validation.model.SignatureData signatureData) {
        if (null == signatureData) {
            return null;
        }

        return new com.example.legalarchive.infrastructure.signature.model.SignatureData(
                signatureData.signature(),
                signatureData.signatureInput(),
                signatureData.componentValues());
    }
}
