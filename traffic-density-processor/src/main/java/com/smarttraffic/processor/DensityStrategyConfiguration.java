package com.smarttraffic.processor;

import com.smarttraffic.domain.SpeedDensityStrategy;
import com.smarttraffic.domain.TrafficDensityStrategy;
import com.smarttraffic.domain.VehicleDensityStrategy;
import com.smarttraffic.domain.WeightedDensityStrategy;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seleccion de la Strategy por configuracion (smarttraffic.density.strategy =
 * vehicle | speed | weighted). Cambiar el algoritmo es cambiar una variable de
 * entorno: el consumidor y el agregado no se tocan (principio Open/Closed).
 */
@Configuration
public class DensityStrategyConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DensityStrategyConfiguration.class);

    @Bean
    TrafficDensityStrategy trafficDensityStrategy(
            @Value("${smarttraffic.density.strategy:weighted}") String name,
            @Value("${smarttraffic.density.road-capacity:100}") double roadCapacity,
            @Value("${smarttraffic.density.free-flow-speed:70}") double freeFlowSpeed) {
        Map<String, Supplier<TrafficDensityStrategy>> catalog = Map.of(
                "vehicle", () -> new VehicleDensityStrategy(roadCapacity),
                "speed", () -> new SpeedDensityStrategy(freeFlowSpeed),
                "weighted", () -> new WeightedDensityStrategy(
                        new VehicleDensityStrategy(roadCapacity), new SpeedDensityStrategy(freeFlowSpeed), 0.6));
        TrafficDensityStrategy strategy = Optional.ofNullable(catalog.get(name))
                .map(Supplier::get)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown density strategy '" + name + "'. Use one of " + catalog.keySet()));
        log.info("Density strategy in use: {} ({})", name, strategy.getClass().getSimpleName());
        return strategy;
    }
}
