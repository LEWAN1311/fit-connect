package org.formation.booking.client;

import org.formation.booking.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", fallbackFactory = NotificationServiceClientFallbackFactory.class)
public interface NotificationServiceClient {

    @PostMapping("/api/notifications")
    void send(@RequestBody NotificationRequest request);
}
