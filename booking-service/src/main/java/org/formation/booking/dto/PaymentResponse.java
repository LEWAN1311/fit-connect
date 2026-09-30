package org.formation.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(
        Long id,
        String paymentReference,
        Long bookingId,
        BigDecimal amount,
        String status) {

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isRefunded() {
        return "REFUNDED".equals(status);
    }
}
