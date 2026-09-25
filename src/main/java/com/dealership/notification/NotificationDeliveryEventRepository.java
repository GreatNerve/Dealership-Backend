package com.dealership.notification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryEventRepository
    extends JpaRepository<NotificationDeliveryEventEntity, UUID> {

  List<NotificationDeliveryEventEntity> findByNotificationIdOrderByOccurredAtDescCreatedAtDesc(
      UUID notificationId);

  List<NotificationDeliveryEventEntity> findByNotificationIdIn(Collection<UUID> notificationIds);

  boolean existsByNotificationIdAndEventTypeIn(
      UUID notificationId, Collection<DeliveryEventType> types);

  @Query(
      """
      SELECT DISTINCT e.notificationId AS notificationId, e.eventType AS eventType
      FROM NotificationDeliveryEventEntity e
      WHERE e.notificationId IN :ids AND e.eventType IN :types
      """)
  List<EventFlag> findDistinctFlags(
      @Param("ids") Collection<UUID> ids, @Param("types") Collection<DeliveryEventType> types);

  interface EventFlag {
    UUID getNotificationId();

    DeliveryEventType getEventType();
  }
}
