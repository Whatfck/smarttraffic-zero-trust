package com.smarttraffic.auth;

import java.security.KeyPair;

/** Puerto (DIP): provee el par de claves RSA con el que la Autoridad firma los JWT. */
public interface SigningKeyProvider {
    KeyPair keyPair();

    /** Identificador de la clave (kid) publicado en el JWKS y en la cabecera del JWT. */
    String keyId();
}
