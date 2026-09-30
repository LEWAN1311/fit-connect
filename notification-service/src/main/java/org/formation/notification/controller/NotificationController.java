package org.formation.notification.controller;

import jakarta.validation.Valid;
import org.formation.notification.dto.NotificationRequest;
import org.formation.notification.entity.Notification;
import org.formation.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Appele par les autres services. */
    @PostMapping
    public ResponseEntity<Notification> send(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(request));
    }

    @GetMapping("/user/{userId}")
    public List<Notification> findByUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }

    @GetMapping("/pending")
    public List<Notification> findPending() {
        return service.findPending();
    }

    @PatchMapping("/{id}/retry")
    public Notification retry(@PathVariable Long id) {
        return service.retry(id);
    }
}
