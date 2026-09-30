package org.formation.booking.exception;

public class NoSpotsAvailableException extends ConflictException {

    public static final String DEFAULT_MESSAGE = "Plus de places disponibles pour ce cours";

    public NoSpotsAvailableException() {
        super(DEFAULT_MESSAGE);
    }

    public NoSpotsAvailableException(String message) {
        super(message);
    }
}
