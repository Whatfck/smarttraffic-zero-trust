package com.smarttraffic.dashboard;

import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Recibe cada estado consolidado, lo convierte en vista y lo difunde. Guarda la
 * ultima vista por zona para que un navegador recien conectado vea el mapa completo.
 */
@Service
public class DashboardService {
    private final ZoneStatusViewMapper mapper;
    private final DashboardBroadcaster broadcaster;
    private final Map<String, ZoneStatusView> latest = new ConcurrentHashMap<>();

    public DashboardService(ZoneStatusViewMapper mapper, DashboardBroadcaster broadcaster) {
        this.mapper = mapper;
        this.broadcaster = broadcaster;
    }

    public void onTrafficUpdate(ZoneTrafficSnapshot snapshot) {
        ZoneStatusView view = mapper.toView(snapshot);
        latest.put(view.zoneId(), view);
        broadcaster.broadcast(view);
    }

    public Collection<ZoneStatusView> currentViews() {
        return List.copyOf(latest.values());
    }
}
