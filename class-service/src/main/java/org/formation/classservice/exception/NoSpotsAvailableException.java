package org.formation.classservice.exception;

public class NoSpotsAvailableException extends ConflictException {

    public NoSpotsAvailableException(String message) {
        super(message);
    }
}
