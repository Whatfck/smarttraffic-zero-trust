package com.smarttraffic.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint /token (OAuth2 client_credentials).
 *
 * Acepta las dos formas en que los microservicios piden token:
 *  1. Formulario estandar OAuth2 (grant_type=client_credentials) con las credenciales
 *     en la cabecera Authorization: Basic o en el cuerpo. Es lo que usan los clientes
 *     Kafka (SASL/OAUTHBEARER) y RabbitMQ (OAuth2 credentials provider).
 *  2. JSON {"clientId": "...", "clientSecret": "..."} para pruebas manuales con curl.
 */
@RestController
public final class TokenController {
    private static final Logger log = LoggerFactory.getLogger(TokenController.class);
    private final TokenIssuer issuer;

    public TokenController(TokenIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> tokenFromForm(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody MultiValueMap<String, String> form) {
        if (!"client_credentials".equals(form.getFirst("grant_type"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        ClientCredentials credentials = ClientCredentials.fromBasicHeader(authorization)
                .orElse(new ClientCredentials(form.getFirst("client_id"), form.getFirst("client_secret")));
        return ResponseEntity.ok(respond(credentials));
    }

    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> tokenFromJson(@RequestBody JsonTokenRequest request) {
        return respond(new ClientCredentials(request.clientId(), request.clientSecret()));
    }

    private Map<String, Object> respond(ClientCredentials credentials) {
        var token = issuer.issue(credentials.clientId(), credentials.clientSecret());
        log.info("JWT issued to client '{}' (expires in {}s)", credentials.clientId(), token.expiresIn());
        return Map.of(
                "access_token", token.accessToken(),
                "token_type", "Bearer",
                "expires_in", token.expiresIn(),
                "scope", token.scope());
    }

    @ExceptionHandler(TokenIssuer.InvalidClientException.class)
    ResponseEntity<Map<String, String>> invalidClient(TokenIssuer.InvalidClientException exception) {
        log.warn("Token request rejected: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Basic realm=\"smarttraffic\"")
                .body(Map.of("error", "invalid_client"));
    }

    public record JsonTokenRequest(String clientId, String clientSecret) {}

    record ClientCredentials(String clientId, String clientSecret) {
        static java.util.Optional<ClientCredentials> fromBasicHeader(String header) {
            if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
                return java.util.Optional.empty();
            }
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new ClientCredentials(
                    urlDecode(decoded.substring(0, separator)), urlDecode(decoded.substring(separator + 1))));
        }

        private static String urlDecode(String value) {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }
}
