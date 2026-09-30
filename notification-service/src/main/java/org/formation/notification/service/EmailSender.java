package org.formation.notification.service;

import org.formation.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Passerelle d'envoi simulee (pas de vrai SMTP/SMS).
 * L'envoi echoue si aucun destinataire n'est renseigne.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    public boolean send(Notification notification) {
        String email = notification.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Notification {} : aucun destinataire, envoi impossible", notification.getId());
            return false;
        }
        // Adresse masquee dans les logs pour ne pas exposer de donnees personnelles
        log.info("[EMAIL SIMULE] a={} type={} sujet=\"{}\"", mask(email), notification.getType(),
                notification.getSubject());
        return true;
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
