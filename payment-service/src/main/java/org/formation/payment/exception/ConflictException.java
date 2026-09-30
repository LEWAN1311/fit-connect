package org.formation.payment.exception;

/** Regle metier violee : traduite en 409 Conflict. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
