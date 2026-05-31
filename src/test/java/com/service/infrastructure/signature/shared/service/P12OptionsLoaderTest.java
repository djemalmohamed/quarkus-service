package com.service.infrastructure.signature.shared.service;

import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import io.vertx.core.net.PfxOptions;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class P12OptionsLoaderTest {

    private final P12OptionsLoader loader = new P12OptionsLoader();

    @Test
    void shouldBuildPfxOptionsFromPath() {
        PfxOptions options = loader.load(
                "signature keystore",
                Optional.of("src/test/resources/certs/agc-signing-keystore.p12"),
                Optional.empty(),
                "changeit"
        );

        assertEquals("src/test/resources/certs/agc-signing-keystore.p12", options.getPath());
        assertEquals("changeit", options.getPassword());
        assertNull(options.getValue());
    }

    @Test
    void shouldBuildPfxOptionsFromBase64() {
        byte[] content = new byte[]{1, 2, 3, 4};
        PfxOptions options = loader.load(
                "partner certificate store",
                Optional.empty(),
                Optional.of(Base64.getEncoder().encodeToString(content)),
                "changeit"
        );

        assertEquals("changeit", options.getPassword());
        assertArrayEquals(content, options.getValue().getBytes());
    }

    @Test
    void shouldRejectWhenPathAndBase64AreBothProvided() {
        assertThrows(SignatureConfigurationException.class, () -> loader.load(
                "partner certificate store",
                Optional.of("store.p12"),
                Optional.of("Zm9v"),
                "changeit"
        ));
    }

    @Test
    void shouldRejectMissingPassword() {
        assertThrows(SignatureConfigurationException.class, () -> loader.load(
                "partner certificate store",
                Optional.of("store.p12"),
                Optional.empty(),
                "   "
        ));
    }

    @Test
    void shouldRejectInvalidBase64Content() {
        assertThrows(SignatureConfigurationException.class, () -> loader.load(
                "partner certificate store",
                Optional.empty(),
                Optional.of("%%%"),
                "changeit"
        ));
    }
}
