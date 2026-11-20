package com.smarttraffic.dashboard;

import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Mapea el mensaje AMQP (contrato de dominio) al DTO de presentacion. */
@Component
public class ZoneStatusViewMapper {
    private static final Map<CongestionState, String> COLORS = new EnumMap<>(Map.of(
            CongestionState.DESPEJADA, "green",
            CongestionState.MODERADA, "amber",
            CongestionState.CONGESTIONADA, "red"));

    public ZoneStatusView toView(ZoneTrafficSnapshot snapshot) {
        return new ZoneStatusView(snapshot.zoneId(),
                (int) Math.round(snapshot.density() * 100),
                snapshot.state().name(),
                COLORS.get(snapshot.state()),
                snapshot.readings(),
                snapshot.updatedAt());
    }
}
