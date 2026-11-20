package com.smarttraffic.archiver;

import com.smarttraffic.domain.TrafficReading;

/**
 * Puerto de persistencia (DIP): el servicio de archivo depende de esta interfaz y no
 * de JPA/PostgreSQL. El adaptador JPA la implementa; en pruebas se usa un mock.
 */
public interface ReadingArchive {
    void store(TrafficReading reading);
}
