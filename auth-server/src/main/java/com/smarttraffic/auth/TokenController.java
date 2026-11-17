package com.smarttraffic.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public final class TokenController {
    private final String expectedClient;
    private final String expectedSecret;
    private final String signingSecret;

    public TokenController(
            @Value("${SMARTTRAFFIC_CLIENT_ID:smarttraffic}") String expectedClient,
            @Value("${SMARTTRAFFIC_CLIENT_SECRET:change-me-in-production}") String expectedSecret,
            @Value("${SMARTTRAFFIC_JWT_SECRET:change-me-to-a-long-random-secret}") String signingSecret) {
        this.expectedClient = expectedClient;
        this.expectedSecret = expectedSecret;
        this.signingSecret = signingSecret;
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        if (!expectedClient.equals(request.clientId()) || !expectedSecret.equals(request.clientSecret())) {
            throw new UnauthorizedException();
        }
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(request.clientId())
                .claim("scope", "traffic:read traffic:write")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return new TokenResponse(token, 900);
    }

    public record TokenRequest(String clientId, String clientSecret) {}
    public record TokenResponse(String accessToken, long expiresIn) {}

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static final class UnauthorizedException extends RuntimeException {}
}
