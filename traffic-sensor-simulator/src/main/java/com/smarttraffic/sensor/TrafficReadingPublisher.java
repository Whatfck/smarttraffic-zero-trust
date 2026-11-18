package com.smarttraffic.sensor;

import com.smarttraffic.domain.TrafficReading;

/** Puerto de salida (DIP): el simulador publica lecturas sin depender de Kafka. */
public interface TrafficReadingPublisher {
    void publish(TrafficReading reading);
}
