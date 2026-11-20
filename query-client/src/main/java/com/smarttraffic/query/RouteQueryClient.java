package com.smarttraffic.query;

import com.smarttraffic.domain.RouteEvaluation;
import com.smarttraffic.domain.RouteQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logica del query_client: arma consultas de trayecto, recuerda las pendientes por
 * requestId y, cuando llega la respuesta asincrona, mide la latencia y la registra.
 */
public class RouteQueryClient {
    private static final Logger log = LoggerFactory.getLogger(RouteQueryClient.class);

    private final ClientIdentity identity;
    private final RouteQuerySender sender;
    private final Supplier<List<String>> routes;
    private final Clock clock;
    private final Map<String, Instant> pending = new ConcurrentHashMap<>();

    public RouteQueryClient(ClientIdentity identity, RouteQuerySender sender,
                            Supplier<List<String>> routes, Clock clock) {
        this.identity = identity;
        this.sender = sender;
        this.routes = routes;
        this.clock = clock;
    }

    public RouteQuery ask() {
        var query = new RouteQuery(UUID.randomUUID().toString(), identity.instanceId(), routes.get());
        pending.put(query.requestId(), clock.instant());
        sender.send(query);
        log.info("[{}] -> query {} route={}", identity.instanceId(), shortId(query.requestId()), query.zoneIds());
        return query;
    }

    public Optional<Duration> onAnswer(RouteEvaluation evaluation) {
        Optional<Duration> latency = Optional.ofNullable(pending.remove(evaluation.requestId()))
                .map(sentAt -> Duration.between(sentAt, clock.instant()));
        log.info("[{}] <- answer {} status={} overall={} latency={}ms {}", identity.instanceId(),
                shortId(evaluation.requestId()), evaluation.status(), evaluation.overall(),
                latency.map(Duration::toMillis).orElse(-1L),
                evaluation.error() == null ? "" : evaluation.error());
        return latency;
    }

    public int pendingCount() {
        return pending.size();
    }

    private static String shortId(String id) {
        return id.substring(0, Math.min(8, id.length()));
    }
}
