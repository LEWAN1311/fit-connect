package org.formation.booking.exception;

/** Paiement refuse par payment-service : traduit en 402 Payment Required. */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
