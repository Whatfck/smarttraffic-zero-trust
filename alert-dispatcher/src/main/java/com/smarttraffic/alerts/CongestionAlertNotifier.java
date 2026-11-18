package com.smarttraffic.alerts;

import com.smarttraffic.domain.CongestionChangedEvent;
import com.smarttraffic.domain.CongestionObserver;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OBSERVER concreto: gatilla una alerta cuando una zona CRUZA el umbral critico
 * (entra a CONGESTIONADA) y avisa cuando se normaliza. Guarda las ultimas alertas.
 */
@Component
public class CongestionAlertNotifier implements CongestionObserver {
    private static final Logger log = LoggerFactory.getLogger(CongestionAlertNotifier.class);
    private static final int HISTORY = 50;

    private final Deque<CongestionChangedEvent> recentAlerts = new ArrayDeque<>();

    @Override
    public void onCongestionChanged(CongestionChangedEvent event) {
        if (event.enteredCritical()) {
            log.warn("ALERTA DE CONGESTION: zona '{}' {} -> {} (densidad {}%)", event.zoneId(),
                    event.previous(), event.current(), Math.round(event.density() * 100));
            remember(event);
        } else if (event.leftCritical()) {
            log.info("Congestion resuelta: zona '{}' {} -> {}", event.zoneId(), event.previous(), event.current());
            remember(event);
        }
    }

    private synchronized void remember(CongestionChangedEvent event) {
        recentAlerts.addFirst(event);
        while (recentAlerts.size() > HISTORY) {
            recentAlerts.removeLast();
        }
    }

    public synchronized List<CongestionChangedEvent> recentAlerts() {
        return List.copyOf(recentAlerts);
    }
}
