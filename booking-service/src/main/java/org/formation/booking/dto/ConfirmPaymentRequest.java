package org.formation.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Corps de PATCH /api/bookings/{id}/confirm. */
public record ConfirmPaymentRequest(
        @NotNull(message = "paymentMethod est obligatoire")
        PaymentMethod paymentMethod,

        @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres")
        String cardLastFour,

        String transactionId) {
}
