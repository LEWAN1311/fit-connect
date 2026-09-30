package org.formation.booking.exception;

/** Service distant injoignable ou circuit ouvert : traduit en 503 Service Unavailable. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
