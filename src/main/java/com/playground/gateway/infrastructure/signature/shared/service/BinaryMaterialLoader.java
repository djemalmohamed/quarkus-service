package com.playground.gateway.infrastructure.signature.shared.service;

import com.playground.gateway.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureInfrastructureException;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

/**
 * Loads binary material from either a local path or a base64-encoded value.
 */
@ApplicationScoped
public class BinaryMaterialLoader {

    public byte[] load(String label, Optional<String> pathOpt, Optional<String> base64Opt) {
        String path = sanitize(pathOpt);
        String base64 = sanitize(base64Opt);
        boolean hasPath = path != null;
        boolean hasBase64 = base64 != null;

        if (hasPath == hasBase64) {
            throw new SignatureConfigurationException(label + ": exactly one of path or base64 must be provided");
        }

        if (hasPath) {
            try {
                return Files.readAllBytes(Path.of(path));
            } catch (Exception error) {
                throw new SignatureInfrastructureException(label + ": unable to read content from path " + path, error);
            }
        }

        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException error) {
            throw new SignatureConfigurationException(label + ": base64 content is invalid", error);
        }
    }

    private String sanitize(Optional<String> value) {
        return value
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .orElse(null);
    }
}
