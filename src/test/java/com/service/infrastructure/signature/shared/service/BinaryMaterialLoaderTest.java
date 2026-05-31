package com.service.infrastructure.signature.shared.service;

import com.service.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryMaterialLoaderTest {

    private final BinaryMaterialLoader loader = new BinaryMaterialLoader();

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadBytesFromPath() throws Exception {
        byte[] content = new byte[]{10, 11, 12};
        Path file = tempDir.resolve("test.crl");
        Files.write(file, content);

        assertArrayEquals(content, loader.load("signature CRL", Optional.of(file.toString()), Optional.empty()));
    }

    @Test
    void shouldLoadBytesFromBase64() {
        byte[] content = new byte[]{21, 22, 23};

        assertArrayEquals(
                content,
                loader.load("signature CRL", Optional.empty(), Optional.of(Base64.getEncoder().encodeToString(content)))
        );
    }

    @Test
    void shouldRejectWhenPathAndBase64AreBothMissing() {
        assertThrows(
                SignatureConfigurationException.class,
                () -> loader.load("signature CRL", Optional.empty(), Optional.empty())
        );
    }

    @Test
    void shouldRejectWhenPathAndBase64AreBothProvided() {
        assertThrows(
                SignatureConfigurationException.class,
                () -> loader.load("signature CRL", Optional.of("/tmp/crl"), Optional.of("dGVzdA=="))
        );
    }

    @Test
    void shouldRejectInvalidBase64Content() {
        SignatureConfigurationException error = assertThrows(
                SignatureConfigurationException.class,
                () -> loader.load("signature CRL", Optional.empty(), Optional.of("%%%"))
        );

        assertTrue(error.getMessage().contains("base64 content is invalid"));
    }

    @Test
    void shouldFailWhenPathCannotBeRead() {
        assertThrows(
                SignatureInfrastructureException.class,
                () -> loader.load("signature CRL", Optional.of(tempDir.resolve("missing.crl").toString()), Optional.empty())
        );
    }

    @Test
    void shouldTreatBlankInputsAsMissing() {
        assertThrows(
                SignatureConfigurationException.class,
                () -> loader.load("signature CRL", Optional.of("   "), Optional.of("   "))
        );
    }
}
