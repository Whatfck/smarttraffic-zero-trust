package com.smarttraffic.query;

import com.smarttraffic.domain.RouteQuery;

/** Puerto de salida (DIP): enviar una consulta de trayecto. */
public interface RouteQuerySender {
    void send(RouteQuery query);
}
