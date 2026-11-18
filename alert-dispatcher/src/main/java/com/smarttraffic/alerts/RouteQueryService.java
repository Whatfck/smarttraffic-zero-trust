package com.smarttraffic.alerts;

import com.smarttraffic.domain.RouteEvaluation;
import com.smarttraffic.domain.RouteQuery;
import com.smarttraffic.domain.TrafficStateReplica;
import com.smarttraffic.domain.ZoneNotFoundException;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Casos de uso del alert_dispatcher:
 *  - mantener la replica local con cada actualizacion del fanout;
 *  - responder consultas de trayecto consultando SOLO la replica local. Una zona
 *    inexistente produce la excepcion de dominio ZoneNotFoundException, que se
 *    controla y se traduce en una respuesta de error (el servicio no se cae).
 */
@Service
public class RouteQueryService {
    private static final Logger log = LoggerFactory.getLogger(RouteQueryService.class);

    private final TrafficStateReplica replica;
    private final RouteAnswerPublisher publisher;

    public RouteQueryService(TrafficStateReplica replica, RouteAnswerPublisher publisher) {
        this.replica = replica;
        this.publisher = publisher;
    }

    public void onTrafficUpdate(ZoneTrafficSnapshot snapshot) {
        replica.apply(snapshot);
    }

    public RouteEvaluation answer(RouteQuery query) {
        RouteEvaluation evaluation;
        try {
            evaluation = replica.evaluate(query);
        } catch (ZoneNotFoundException unknownZone) {
            log.warn("Query {} from {} references unknown zone '{}'",
                    query.requestId(), query.clientId(), unknownZone.zoneId());
            evaluation = RouteEvaluation.zoneNotFound(query.requestId(), unknownZone);
        }
        publisher.answer(query.clientId(), evaluation);
        log.info("Answered query {} from {}: {} {}", query.requestId(), query.clientId(),
                evaluation.status(), evaluation.overall());
        return evaluation;
    }
}
