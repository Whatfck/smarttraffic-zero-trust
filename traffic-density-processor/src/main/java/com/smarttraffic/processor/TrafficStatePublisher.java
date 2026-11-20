package com.smarttraffic.processor;

import com.smarttraffic.domain.ZoneTrafficSnapshot;

/**
 * Puerto de salida (DIP): el servicio de procesamiento publica el estado consolidado
 * sin saber que detras hay RabbitMQ. En pruebas se reemplaza por un mock.
 */
public interface TrafficStatePublisher {
    void publish(ZoneTrafficSnapshot snapshot);
}
