package org.formation.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vue d'un cours renvoyee par class-service (seuls les champs utiles sont mappes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FitnessClassDto(
        Long id,
        String name,
        String instructor,
        LocalDateTime dateTime,
        BigDecimal price,
        Integer maxParticipants,
        Integer currentParticipants,
        String status) {

    public int availableSpots() {
        return maxParticipants - currentParticipants;
    }
}
