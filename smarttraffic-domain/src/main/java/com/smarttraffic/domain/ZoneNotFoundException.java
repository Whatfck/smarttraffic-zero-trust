package com.smarttraffic.domain;

/** Excepcion de dominio controlada: se consulto una zona que no existe en la replica. */
public final class ZoneNotFoundException extends RuntimeException {
    private final String zoneId;

    public ZoneNotFoundException(String zoneId) {
        super("Unknown traffic zone: " + zoneId);
        this.zoneId = zoneId;
    }

    public String zoneId() {
        return zoneId;
    }
}
