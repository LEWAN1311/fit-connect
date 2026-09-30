package org.formation.notification.service;

import org.formation.notification.dto.NotificationRequest;
import org.formation.notification.entity.Notification;
import org.formation.notification.entity.NotificationStatus;
import org.formation.notification.exception.ConflictException;
import org.formation.notification.exception.ResourceNotFoundException;
import org.formation.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final EmailSender emailSender;

    public NotificationService(NotificationRepository repository, EmailSender emailSender) {
        this.repository = repository;
        this.emailSender = emailSender;
    }

    /** Persiste la notification en PENDING puis tente l'envoi (SENT ou FAILED). */
    @Transactional
    public Notification send(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.userId());
        notification.setEmail(request.email());
        notification.setType(request.type());
        notification.setSubject(request.subject());
        notification.setContent(request.content());
        notification.setStatus(NotificationStatus.PENDING);
        notification = repository.save(notification);
        return dispatch(notification);
    }

    @Transactional
    public Notification retry(Long id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable : id=" + id));
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new ConflictException("La notification " + id + " a deja ete envoyee");
        }
        return dispatch(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> findByUser(Long userId) {
        return repository.findByUserIdOrderBySentDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> findPending() {
        return repository.findByStatusOrderBySentDateAsc(NotificationStatus.PENDING);
    }

    private Notification dispatch(Notification notification) {
        boolean sent = emailSender.send(notification);
        notification.setStatus(sent ? NotificationStatus.SENT : NotificationStatus.FAILED);
        notification.setSentDate(LocalDateTime.now());
        return repository.save(notification);
    }
}
