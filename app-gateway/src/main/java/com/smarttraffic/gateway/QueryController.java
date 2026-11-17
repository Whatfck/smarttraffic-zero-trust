package com.smarttraffic.gateway;

import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zones")
public final class QueryController {
    private final TrafficStateReplica replica;

    public QueryController(TrafficStateReplica replica) {
        this.replica = replica;
    }

    @GetMapping("/{zoneId}")
    public ZoneTrafficSnapshot query(@PathVariable String zoneId) {
        var result = replica.get(zoneId);
        if (result == null) throw new ZoneNotFoundException(zoneId);
        return result;
    }

    @PutMapping("/{zoneId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ZoneTrafficSnapshot update(@PathVariable String zoneId, @RequestBody ZoneTrafficSnapshot snapshot) {
        replica.put(snapshot);
        return snapshot;
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static final class ZoneNotFoundException extends RuntimeException {
        ZoneNotFoundException(String zoneId) { super("Unknown traffic zone: " + zoneId); }
    }
}
