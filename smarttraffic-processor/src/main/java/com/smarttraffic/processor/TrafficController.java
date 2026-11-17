package com.smarttraffic.processor;

import com.smarttraffic.domain.TrafficReading;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traffic")
public final class TrafficController {
    private final TrafficProcessingService service;

    public TrafficController(TrafficProcessingService service) {
        this.service = service;
    }

    @PostMapping("/readings")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Object receive(@RequestBody ReadingRequest request) {
        return service.process(new TrafficReading(request.zoneId(), request.vehicles(),
                request.averageSpeed(), request.observedAt() == null ? Instant.now() : request.observedAt()));
    }

    public record ReadingRequest(String zoneId, int vehicles, double averageSpeed, Instant observedAt) {}
}
