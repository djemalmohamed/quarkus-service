package com.playground.gateway.infrastructure.signature.validation.service;

import com.playground.gateway.infrastructure.signature.config.SignatureConfig;
import com.playground.gateway.infrastructure.signature.config.SignatureMaterialConfig;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.playground.gateway.infrastructure.signature.shared.service.BinaryMaterialLoader;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;

/**
 * Validates whether a trusted signature certificate has been revoked.
 *
 * <p>The current implementation accepts either a local CRL file or a base64-encoded CRL payload
 * so the project can simulate a real CA and CRL flow locally while staying compatible with a
 * future Vault-backed setup. A later enterprise version could still swap this implementation for
 * CRL distribution points, OCSP or a platform PKI integration without changing the validation
 * engine contract.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CertificateRevocationValidationService {

    private final SignatureConfig config;

    private final SignatureMaterialConfig materialConfig;

    private final BinaryMaterialLoader binaryMaterialLoader;

    private X509Certificate trustedCaCertificate;
    private X509CRL certificateRevocationList;

    /**
     * Loads the configured CRL once at startup when revocation checks are enabled.
     *
     * <p>The gateway loads the CRL eagerly from either a local path or an in-memory base64 value
     * so it does not have to reparse it for every validated request.</p>
     */
    @PostConstruct
    void init() {
        trustedCaCertificate = materialConfig.trust()
                .flatMap(SignatureMaterialConfig.TrustConfig::ca)
                .map(this::loadTrustedCaCertificate)
                .orElse(null);

        if (!config.revocation().enabled()) {
            certificateRevocationList = null;
            return;
        }

        if (trustedCaCertificate == null) {
            throw new SignatureConfigurationException(
                    "Missing required trusted CA configuration under agw.connectivity.signature.ds.trust.ca"
            );
        }

        SignatureMaterialConfig.CrlConfig crlConfig = materialConfig.trust()
                .flatMap(SignatureMaterialConfig.TrustConfig::crl)
                .orElseThrow(() -> new SignatureConfigurationException(
                        "Missing required CRL configuration under agw.connectivity.signature.ds.trust.crl"
                ));

        byte[] crlContent = binaryMaterialLoader.load(
                "signature CRL",
                crlConfig.path(),
                crlConfig.base64()
        );
        certificateRevocationList = loadCrl(crlContent);
        verifyCrlTrustedByCa(certificateRevocationList, trustedCaCertificate);
    }

    /**
     * Determines whether the given certificate was issued by the configured trusted CA.
     *
     * @param certificate signer certificate resolved for the inbound message
     * @return {@code true} when the certificate chains to the configured trusted CA, otherwise {@code false}
     */
    public boolean isTrusted(X509Certificate certificate) {
        if (certificate == null || trustedCaCertificate == null) {
            return true;
        }

        try {
            if (!certificate.getIssuerX500Principal().equals(trustedCaCertificate.getSubjectX500Principal())) {
                return false;
            }

            certificate.verify(trustedCaCertificate.getPublicKey());
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    /**
     * Determines whether the given certificate has been revoked according to the configured CRL.
     *
     * @param certificate signer certificate resolved for the inbound message
     * @return {@code true} when the certificate is present in the CRL, otherwise {@code false}
     */
    public boolean isRevoked(X509Certificate certificate) {
        if (!config.revocation().enabled() || certificate == null || certificateRevocationList == null) {
            return false;
        }

        return certificateRevocationList.isRevoked(certificate);
    }

    /**
     * Loads the certificate revocation list from binary content.
     *
     * @param crlContent CRL bytes
     * @return parsed X.509 CRL
     */
    private X509CRL loadCrl(byte[] crlContent) {
        try (InputStream inputStream = new ByteArrayInputStream(crlContent)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509CRL) certificateFactory.generateCRL(inputStream);
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to load certificate revocation list", error);
        }
    }

    /**
     * Loads the trusted CA certificate configured for signature verification.
     *
     * @param certificateConfig trusted CA material configuration
     * @return parsed X.509 CA certificate
     */
    private X509Certificate loadTrustedCaCertificate(SignatureMaterialConfig.CertificateConfig certificateConfig) {
        byte[] certificateContent = binaryMaterialLoader.load(
                "trusted CA certificate",
                certificateConfig.path(),
                certificateConfig.base64()
        );

        try (InputStream inputStream = new ByteArrayInputStream(certificateContent)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            if (!(certificateFactory.generateCertificate(inputStream) instanceof X509Certificate certificate)) {
                throw new SignatureConfigurationException("Configured trusted CA material is not an X.509 certificate");
            }
            return certificate;
        } catch (SignatureConfigurationException error) {
            throw error;
        } catch (CertificateException error) {
            throw new SignatureInfrastructureException("Unable to load trusted CA certificate", error);
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to load trusted CA certificate", error);
        }
    }

    /**
     * Verifies that the configured CRL was issued and signed by the configured trusted CA.
     *
     * @param crl parsed certificate revocation list
     * @param caCertificate parsed trusted CA certificate
     */
    private void verifyCrlTrustedByCa(X509CRL crl, X509Certificate caCertificate) {
        try {
            if (!crl.getIssuerX500Principal().equals(caCertificate.getSubjectX500Principal())) {
                throw new SignatureConfigurationException("Configured CRL issuer does not match the trusted CA");
            }

            crl.verify(caCertificate.getPublicKey());
        } catch (SignatureConfigurationException error) {
            throw error;
        } catch (Exception error) {
            throw new SignatureConfigurationException("Configured CRL is not signed by the trusted CA", error);
        }
    }
}
