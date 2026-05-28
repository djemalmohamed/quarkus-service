package com.service.infrastructure.signature.shared.service;

import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PfxOptions;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Base64;
import java.util.Optional;

/**
 * Builds {@link PfxOptions} from either a local path or a base64-encoded PKCS12 payload.
 */
@ApplicationScoped
public class P12OptionsLoader {

    public PfxOptions load(String label, Optional<String> pathOpt, Optional<String> base64Opt, String password) {
        String path = sanitize(pathOpt);
        String base64 = sanitize(base64Opt);
        boolean hasPath = path != null;
        boolean hasBase64 = base64 != null;

        if (hasPath == hasBase64) {
            throw new SignatureConfigurationException(label + ": exactly one of path or base64 must be provided");
        }
        if (password == null || password.isBlank()) {
            throw new SignatureConfigurationException(label + ": password is missing");
        }

        PfxOptions options = new PfxOptions().setPassword(password);
        if (hasPath) {
            options.setPath(path);
            return options;
        }

        try {
            options.setValue(Buffer.buffer(Base64.getDecoder().decode(base64)));
            return options;
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
