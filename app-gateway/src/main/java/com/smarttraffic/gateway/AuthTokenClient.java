package com.smarttraffic.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenClient {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenClient.class);

    private final String authServerUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String currentToken;

    public AuthTokenClient(
            @Value("${AUTH_SERVER_URL:http://localhost:8081}") String authServerUrl,
            @Value("${SMARTTRAFFIC_CLIENT_ID:smarttraffic}") String clientId,
            @Value("${SMARTTRAFFIC_CLIENT_SECRET:change-me-in-production}") String clientSecret,
            ObjectMapper objectMapper) {
        this.authServerUrl = authServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @PostConstruct
    public void authenticate() {
        try {
            requestToken();
        } catch (Exception e) {
            log.warn("Initial S2S authentication against Auth Server at {} failed: {}. Will retry as needed.",
                    authServerUrl, e.getMessage());
        }
    }

    public synchronized Optional<String> requestToken() {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "clientId", clientId,
                    "clientSecret", clientSecret
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authServerUrl + "/token"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var json = objectMapper.readTree(response.body());
                this.currentToken = json.get("accessToken").asText();
                log.info("S2S authentication successful against {}. JWT acquired (expires in {}s).",
                        authServerUrl, json.has("expiresIn") ? json.get("expiresIn").asLong() : 900);
                return Optional.of(currentToken);
            } else {
                log.error("S2S authentication rejected by Auth Server. HTTP status: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Could not obtain S2S JWT from {}: {}", authServerUrl, e.getMessage());
        }
        return Optional.ofNullable(currentToken);
    }

    public Optional<String> getToken() {
        return Optional.ofNullable(currentToken);
    }
}
