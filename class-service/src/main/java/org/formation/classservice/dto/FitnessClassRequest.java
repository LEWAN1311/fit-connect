package org.formation.classservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.formation.classservice.entity.Category;
import org.formation.classservice.entity.ClassStatus;
import org.formation.classservice.entity.Level;
import org.formation.classservice.validation.ValidDuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Donnees de creation / mise a jour d'un cours.
 * currentParticipants n'est pas modifiable ici : il est gere par increment/decrement.
 */
public record FitnessClassRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(min = 3, message = "Le nom doit contenir au moins 3 caracteres")
        String name,

        @NotBlank(message = "La description est obligatoire")
        String description,

        @NotBlank(message = "L'instructeur est obligatoire")
        String instructor,

        @NotBlank(message = "La salle est obligatoire")
        String gymLocation,

        @NotNull(message = "La categorie est obligatoire")
        Category category,

        @NotNull(message = "Le niveau est obligatoire")
        Level level,

        @NotNull(message = "La duree est obligatoire")
        @ValidDuration
        Integer durationMinutes,

        @NotNull(message = "Le nombre maximum de participants est obligatoire")
        @Min(value = 5, message = "Minimum 5 participants")
        @Max(value = 30, message = "Maximum 30 participants")
        Integer maxParticipants,

        @NotNull(message = "Le prix est obligatoire")
        @DecimalMin(value = "5.00", message = "Le prix doit etre superieur ou egal a 5.00")
        BigDecimal price,

        @NotNull(message = "La date est obligatoire")
        @FutureOrPresent(message = "La date du cours doit etre dans le futur")
        LocalDateTime dateTime,

        // Optionnel, utilise uniquement en mise a jour (ex : COMPLETED)
        ClassStatus status) {
}
