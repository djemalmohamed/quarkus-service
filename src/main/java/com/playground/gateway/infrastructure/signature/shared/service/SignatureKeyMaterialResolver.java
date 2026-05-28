package com.playground.gateway.infrastructure.signature.shared.service;

import com.playground.gateway.infrastructure.signature.config.SignatureMaterialConfig;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureInfrastructureException;
import com.playground.gateway.infrastructure.signature.shared.model.ResolvedKeyMaterial;
import io.vertx.core.net.PfxOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves signature material directly from the {@code keyId}-based naming convention.
 *
 * <p>The current runtime uses local files or Vault-provided base64 values behind properties such as
 * {@code agw.connectivity.signature.ds.keys.<keyId>.pkcs12.*} and
 * {@code agw.connectivity.signature.ds.keys.<keyId>.certificate.*}. This keeps the on-wire identifier
 * stable while allowing a future HSM-backed implementation to swap only the lookup backend.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SignatureKeyMaterialResolver {

    private static final String PROPERTY_PREFIX = "agw.connectivity.signature.ds.keys.";
    private final Map<String, Optional<ResolvedKeyMaterial>> signingMaterialCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<ResolvedKeyMaterial>> verificationMaterialCache = new ConcurrentHashMap<>();

    private final SignatureMaterialConfig materialConfig;

    private final P12OptionsLoader p12OptionsLoader;

    private final BinaryMaterialLoader binaryMaterialLoader;

    public Optional<ResolvedKeyMaterial> resolveForSigning(String keyId) {
        String normalizedKeyId = normalizeKeyId(keyId);
        if (normalizedKeyId == null) {
            return Optional.empty();
        }
        return signingMaterialCache.computeIfAbsent(normalizedKeyId, this::loadSigningMaterial);
    }

    public Optional<ResolvedKeyMaterial> resolveForVerification(String keyId) {
        String normalizedKeyId = normalizeKeyId(keyId);
        if (normalizedKeyId == null) {
            return Optional.empty();
        }
        return verificationMaterialCache.computeIfAbsent(normalizedKeyId, this::loadVerificationMaterial);
    }

    private Optional<ResolvedKeyMaterial> loadSigningMaterial(String keyId) {
        SignatureMaterialConfig.KeyEntry entry = keyEntry(keyId);
        if (entry == null || !hasPkcs12Material(entry)) {
            return Optional.empty();
        }
        return Optional.of(fromPkcs12(keyId, entry.pkcs12().orElseThrow(), true));
    }

    private Optional<ResolvedKeyMaterial> loadVerificationMaterial(String keyId) {
        SignatureMaterialConfig.KeyEntry entry = keyEntry(keyId);
        if (entry == null) {
            return Optional.empty();
        }
        if (hasCertificateMaterial(entry)) {
            return Optional.of(fromCertificate(keyId, entry.certificate().orElseThrow()));
        }
        if (hasPkcs12Material(entry)) {
            return Optional.of(fromPkcs12(keyId, entry.pkcs12().orElseThrow(), false));
        }
        return Optional.empty();
    }

    private ResolvedKeyMaterial fromPkcs12(
            String keyId,
            SignatureMaterialConfig.Pkcs12Config pkcs12Config,
            boolean includePrivateKey
    ) {
        String password = requiredProperty(pkcs12Config.password(), pkcs12PasswordPropertyName(keyId));
        PfxOptions pfxOptions = p12OptionsLoader.load(
                "PKCS12 for keyId " + keyId,
                pkcs12Config.path(),
                pkcs12Config.base64(),
                password
        );
        KeyStore keyStore = loadPkcs12Store(keyId, pfxOptions, password);
        String alias = resolveAlias(keyId, keyStore, pkcs12Config.alias());

        try {
            X509Certificate certificate = extractCertificate(keyStore, alias);
            PrivateKey privateKey = includePrivateKey ? extractPrivateKey(keyStore, alias, password) : null;

            return new ResolvedKeyMaterial(
                    keyId,
                    includePrivateKey ? "pkcs12-key" : "pkcs12-certificate",
                    certificate.getPublicKey(),
                    privateKey,
                    certificate
            );
        } catch (SignatureConfigurationException error) {
            throw error;
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to resolve PKCS12 material for keyId " + keyId, error);
        }
    }

    private ResolvedKeyMaterial fromCertificate(String keyId, SignatureMaterialConfig.CertificateConfig certificateConfig) {
        byte[] certificateContent = binaryMaterialLoader.load(
                "certificate for keyId " + keyId,
                certificateConfig.path(),
                certificateConfig.base64()
        );

        try (InputStream inputStream = new ByteArrayInputStream(certificateContent)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            if (!(certificateFactory.generateCertificate(inputStream) instanceof X509Certificate certificate)) {
                throw new SignatureConfigurationException("Configured certificate for keyId " + keyId + " is not an X.509 certificate");
            }

            return new ResolvedKeyMaterial(
                    keyId,
                    "certificate",
                    certificate.getPublicKey(),
                    null,
                    certificate
            );
        } catch (SignatureConfigurationException error) {
            throw error;
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to resolve certificate for keyId " + keyId, error);
        }
    }

    private KeyStore loadPkcs12Store(String keyId, PfxOptions pfxOptions, String password) {
        try (InputStream inputStream = openPkcs12Stream(pfxOptions)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, password.toCharArray());
            return keyStore;
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to load PKCS12 store for keyId " + keyId, error);
        }
    }

    private X509Certificate extractCertificate(KeyStore keyStore, String alias) throws Exception {
        java.security.cert.Certificate certificateEntry = keyStore.getCertificate(alias);
        if (!(certificateEntry instanceof X509Certificate certificate)) {
            throw new SignatureConfigurationException("Missing X.509 certificate for alias " + alias);
        }
        return certificate;
    }

    private PrivateKey extractPrivateKey(KeyStore keyStore, String alias, String password) throws Exception {
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
        if (privateKey == null) {
            throw new SignatureConfigurationException("Missing private key for alias " + alias);
        }
        return privateKey;
    }

    private String resolveAlias(String keyId, KeyStore keyStore, Optional<String> configuredAlias) {
        Optional<String> sanitizedAlias = sanitize(configuredAlias);
        if (sanitizedAlias.isPresent()) {
            return sanitizedAlias.get();
        }

        try {
            Enumeration<String> aliases = keyStore.aliases();
            if (!aliases.hasMoreElements()) {
                throw new SignatureConfigurationException("No alias found in PKCS12 for keyId " + keyId);
            }

            String firstAlias = aliases.nextElement();
            if (aliases.hasMoreElements()) {
                throw new SignatureConfigurationException(
                        "Multiple aliases found in PKCS12 for keyId " + keyId + "; configure " + pkcs12AliasPropertyName(keyId)
                );
            }
            return firstAlias;
        } catch (SignatureConfigurationException error) {
            throw error;
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to resolve PKCS12 alias for keyId " + keyId, error);
        }
    }

    private InputStream openPkcs12Stream(PfxOptions pfxOptions) throws Exception {
        if (pfxOptions.getPath() != null && !pfxOptions.getPath().isBlank()) {
            return Files.newInputStream(Path.of(pfxOptions.getPath()));
        }
        if (pfxOptions.getValue() != null) {
            return new ByteArrayInputStream(pfxOptions.getValue().getBytes());
        }
        throw new SignatureConfigurationException("PKCS12 options do not contain a path or in-memory value");
    }

    private boolean hasPkcs12Material(SignatureMaterialConfig.KeyEntry entry) {
        return entry.pkcs12()
                .filter(pkcs12Config -> hasValue(pkcs12Config.path()) || hasValue(pkcs12Config.base64()))
                .isPresent();
    }

    private boolean hasCertificateMaterial(SignatureMaterialConfig.KeyEntry entry) {
        return entry.certificate()
                .filter(certificateConfig -> hasValue(certificateConfig.path()) || hasValue(certificateConfig.base64()))
                .isPresent();
    }

    private String requiredProperty(Optional<String> value, String name) {
        return sanitize(value)
                .orElseThrow(() -> new SignatureConfigurationException("Missing required property: " + name));
    }

    private SignatureMaterialConfig.KeyEntry keyEntry(String keyId) {
        if (keyId == null) {
            return null;
        }
        return materialConfig.keys().get(keyId);
    }

    private boolean hasValue(Optional<String> value) {
        return sanitize(value).isPresent();
    }

    private Optional<String> sanitize(Optional<String> value) {
        return value
                .map(String::trim)
                .filter(entry -> !entry.isBlank());
    }

    private String pkcs12AliasPropertyName(String keyId) {
        return PROPERTY_PREFIX + keyId + ".pkcs12.alias";
    }

    private String pkcs12PasswordPropertyName(String keyId) {
        return PROPERTY_PREFIX + keyId + ".pkcs12.password";
    }

    private String normalizeKeyId(String keyId) {
        if (keyId == null) {
            return null;
        }

        String trimmed = keyId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
