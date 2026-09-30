package org.formation.booking.dto;

import java.math.BigDecimal;

/** Requete envoyee a payment-service (POST /api/payments). */
public record PaymentRequest(
        Long bookingId,
        String bookingReference,
        Long userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String cardLastFour,
        String transactionId) {
}
