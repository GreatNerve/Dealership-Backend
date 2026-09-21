package com.dealership.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

  boolean existsByIdempotencyKey(String idempotencyKey);

  boolean existsByIdempotencyKeyAndStatus(String idempotencyKey, NotificationStatus status);

  Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);

  List<NotificationEntity> findByReminderIdIn(Collection<UUID> reminderIds);
}
