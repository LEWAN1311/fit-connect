package org.formation.classservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.formation.classservice.entity.Category;
import org.formation.classservice.entity.ClassStatus;
import org.formation.classservice.entity.Level;
import org.formation.classservice.validation.ValidDuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mise a jour partielle d'un cours (PATCH) : un champ absent ou null n'est pas modifie.
 * Les champs fournis respectent les memes contraintes qu'a la creation.
 * currentParticipants n'est pas modifiable ici : il est gere par increment/decrement.
 */
public record FitnessClassUpdateRequest(
        @Pattern(regexp = NOT_BLANK, message = "Le nom ne peut pas etre vide")
        @Size(min = 3, message = "Le nom doit contenir au moins 3 caracteres")
        String name,

        @Pattern(regexp = NOT_BLANK, message = "La description ne peut pas etre vide")
        String description,

        @Pattern(regexp = NOT_BLANK, message = "L'instructeur ne peut pas etre vide")
        String instructor,

        @Pattern(regexp = NOT_BLANK, message = "La salle ne peut pas etre vide")
        String gymLocation,

        Category category,

        Level level,

        @ValidDuration
        Integer durationMinutes,

        @Min(value = 5, message = "Minimum 5 participants")
        @Max(value = 30, message = "Maximum 30 participants")
        Integer maxParticipants,

        @DecimalMin(value = "5.00", message = "Le prix doit etre superieur ou egal a 5.00")
        BigDecimal price,

        @FutureOrPresent(message = "La date du cours doit etre dans le futur")
        LocalDateTime dateTime,

        // Ex : COMPLETED une fois le cours termine
        ClassStatus status) {

    /** Au moins un caractere non blanc ; null reste accepte (= champ non modifie). */
    private static final String NOT_BLANK = "(?s).*\\S.*";
}
