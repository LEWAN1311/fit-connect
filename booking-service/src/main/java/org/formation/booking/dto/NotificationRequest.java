package org.formation.booking.dto;

/** Requete envoyee a notification-service (POST /api/notifications). */
public record NotificationRequest(
        Long userId,
        String email,
        NotificationType type,
        String subject,
        String content) {
}
