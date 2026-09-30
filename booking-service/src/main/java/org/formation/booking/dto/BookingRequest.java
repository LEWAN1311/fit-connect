package org.formation.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotNull(message = "userId est obligatoire")
        Long userId,

        @NotBlank(message = "userEmail est obligatoire")
        @Email(message = "userEmail invalide")
        String userEmail,

        @NotBlank(message = "userName est obligatoire")
        String userName,

        @NotNull(message = "classId est obligatoire")
        Long classId,

        @NotNull(message = "numberOfSpots est obligatoire")
        @Min(value = 1, message = "Minimum 1 place")
        @Max(value = 4, message = "Maximum 4 places par reservation")
        Integer numberOfSpots) {
}
