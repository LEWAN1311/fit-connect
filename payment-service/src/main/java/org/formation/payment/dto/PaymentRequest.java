package org.formation.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.formation.payment.entity.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "bookingId est obligatoire")
        Long bookingId,

        String bookingReference,

        @NotNull(message = "userId est obligatoire")
        Long userId,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0", message = "Le montant doit etre positif")
        BigDecimal amount,

        @NotNull(message = "Le moyen de paiement est obligatoire")
        PaymentMethod paymentMethod,

        @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres")
        String cardLastFour,

        String transactionId) {
}
