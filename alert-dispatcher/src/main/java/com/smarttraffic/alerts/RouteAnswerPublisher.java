package com.smarttraffic.alerts;

import com.smarttraffic.domain.RouteEvaluation;

/** Puerto de salida (DIP): enviar la respuesta de una consulta al cliente que la hizo. */
public interface RouteAnswerPublisher {
    void answer(String clientId, RouteEvaluation evaluation);
}
