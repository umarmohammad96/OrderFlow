package com.orderflow.notification.repository;

import com.orderflow.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByIdDesc(Long userId);

    List<Notification> findByOrderId(Long orderId);

    long countByEventId(UUID eventId);
}
