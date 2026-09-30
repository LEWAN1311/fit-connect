package org.formation.booking.client;

import org.formation.booking.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * La notification n'est pas critique : si notification-service est indisponible,
 * on journalise et la reservation continue (degradation gracieuse).
 */
@Component
public class NotificationServiceClientFallbackFactory implements FallbackFactory<NotificationServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClientFallbackFactory.class);

    @Override
    public NotificationServiceClient create(Throwable cause) {
        return request -> log.warn("Notification {} non envoyee (notification-service indisponible) : {}",
                request.type(), cause.toString());
    }
}
