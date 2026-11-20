package com.smarttraffic.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador: carga el par RSA-2048 desde un volumen persistente o lo genera la
 * primera vez. Asi la clave sobrevive a reinicios del contenedor y los brokers no
 * tienen que volver a descargar el JWKS. La clave privada nunca sale de vm-auth.
 */
@Component
public final class FileRsaKeyProvider implements SigningKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(FileRsaKeyProvider.class);

    private final KeyPair keyPair;
    private final String keyId;

    public FileRsaKeyProvider(@Value("${smarttraffic.auth.key-dir:./keys}") String keyDir) {
        this.keyPair = loadOrGenerate(Path.of(keyDir));
        this.keyId = thumbprint(keyPair);
    }

    @Override
    public KeyPair keyPair() {
        return keyPair;
    }

    @Override
    public String keyId() {
        return keyId;
    }

    private static KeyPair loadOrGenerate(Path dir) {
        Path privateFile = dir.resolve("signing-key.pk8");
        Path publicFile = dir.resolve("signing-key.pub");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (Files.exists(privateFile) && Files.exists(publicFile)) {
                log.info("Loading RSA signing key from {}", dir);
                return new KeyPair(
                        factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile))),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privateFile))));
            }
            log.info("Generating new RSA-2048 signing key in {}", dir);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair generated = generator.generateKeyPair();
            Files.createDirectories(dir);
            Files.write(privateFile, generated.getPrivate().getEncoded());
            Files.write(publicFile, generated.getPublic().getEncoded());
            return generated;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot access key directory " + dir, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot build RSA key pair", e);
        }
    }

    private static String thumbprint(KeyPair pair) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
