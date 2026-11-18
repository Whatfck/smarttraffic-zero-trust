package com.smarttraffic.alerts;

import com.smarttraffic.domain.CongestionObserver;
import com.smarttraffic.domain.TrafficStateReplica;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AlertDispatcherApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlertDispatcherApplication.class, args);
    }

    /**
     * Replica local del mapa de estados (objeto de dominio puro). Los observadores se
     * suscriben aqui: la replica no sabe quien escucha (Observer desacoplado).
     */
    @Bean
    TrafficStateReplica trafficStateReplica(List<CongestionObserver> observers) {
        TrafficStateReplica replica = new TrafficStateReplica();
        observers.forEach(replica::subscribe);
        return replica;
    }
}
