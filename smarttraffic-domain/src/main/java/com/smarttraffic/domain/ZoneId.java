package com.smarttraffic.domain;

import java.util.regex.Pattern;

/**
 * Identificador inmutable de una zona de trafico (Value Object). Valida Fail-Fast que
 * no sea nulo/vacio y que tenga un formato seguro (letras, numeros, punto, guion y
 * guion bajo; maximo 64), asi ningun identificador invalido entra al dominio.
 */
public record ZoneId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public ZoneId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("zoneId has an invalid format: " + value);
        }
    }

    public static ZoneId of(String value) {
        return new ZoneId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
