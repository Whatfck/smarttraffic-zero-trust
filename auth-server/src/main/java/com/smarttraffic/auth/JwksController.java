package com.smarttraffic.auth;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publica la CLAVE PUBLICA de firma en formato JWKS (RFC 7517). Kafka y RabbitMQ la
 * descargan para verificar la firma de cada JWT sin conocer nunca la clave privada.
 */
@RestController
public final class JwksController {
    private final SigningKeyProvider keys;

    public JwksController(SigningKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) keys.keyPair().getPublic();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keys.keyId(),
                "n", base64Url(publicKey.getModulus()),
                "e", base64Url(publicKey.getPublicExponent()))));
    }

    /**
     * La misma clave publica en formato PEM. RabbitMQ exige https para descargar un
     * JWKS, asi que en vm-data se "fija" (key pinning) esta clave durante el
     * aprovisionamiento y el broker verifica las firmas sin depender de la red.
     */
    @GetMapping(value = "/.well-known/signing-key.pem", produces = "text/plain")
    public String signingKeyPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .encodeToString(keys.keyPair().getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {   // quitar byte de signo
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
