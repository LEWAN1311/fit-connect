package org.formation.notification.repository;

import org.formation.notification.entity.Notification;
import org.formation.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderBySentDateDesc(Long userId);

    List<Notification> findByStatusOrderBySentDateAsc(NotificationStatus status);
}
