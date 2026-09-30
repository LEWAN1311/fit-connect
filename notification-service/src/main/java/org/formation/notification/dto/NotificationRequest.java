package org.formation.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.formation.notification.entity.NotificationType;

public record NotificationRequest(
        @NotNull(message = "userId est obligatoire")
        Long userId,

        @Email(message = "Email invalide")
        String email,

        @NotNull(message = "Le type est obligatoire")
        NotificationType type,

        @NotBlank(message = "Le sujet est obligatoire")
        String subject,

        @NotBlank(message = "Le contenu est obligatoire")
        String content) {
}
