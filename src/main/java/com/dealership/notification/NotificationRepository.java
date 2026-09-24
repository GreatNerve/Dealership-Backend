package com.dealership.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

  boolean existsByIdempotencyKey(String idempotencyKey);

  boolean existsByIdempotencyKeyAndStatus(String idempotencyKey, NotificationStatus status);

  Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);

  List<NotificationEntity> findByReminderIdIn(Collection<UUID> reminderIds);

  @Query(
      """
      SELECT n FROM NotificationEntity n
      WHERE n.dealershipId = :dealershipId
        AND (:hasStatus = false OR n.status = :status)
        AND (:hasGeneration = false OR n.generation = :generation)
        AND (:hasChannel = false OR n.channel = :channel)
        AND (:hasAppointmentId = false OR n.appointmentId = :appointmentId)
        AND (:hasFrom = false OR n.createdAt >= :fromTs)
        AND (:hasTo = false OR n.createdAt < :toTs)
        AND (:hasEvent = false OR EXISTS (
          SELECT e FROM NotificationDeliveryEventEntity e
          WHERE e.notificationId = n.id AND e.eventType = :eventType))
        AND (:q IS NULL
          OR lower(cast(n.status as string)) LIKE :q ESCAPE '\\'
          OR lower(cast(n.generation as string)) LIKE :q ESCAPE '\\'
          OR lower(cast(n.channel as string)) LIKE :q ESCAPE '\\'
          OR EXISTS (
            SELECT 1
            FROM AppointmentEntity a, VehicleEntity v, CustomerEntity c, UserEntity u
            WHERE a.id = n.appointmentId
              AND v.id = a.vehicleId
              AND c.id = a.customerId
              AND u.id = c.userId
              AND (lower(v.registrationNumber) LIKE :q ESCAPE '\\'
                OR lower(v.make) LIKE :q ESCAPE '\\'
                OR lower(v.model) LIKE :q ESCAPE '\\'
                OR lower(coalesce(u.name, '')) LIKE :q ESCAPE '\\')))
      """)
  Page<NotificationEntity> searchStaff(
      @Param("dealershipId") UUID dealershipId,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") NotificationStatus status,
      @Param("hasGeneration") boolean hasGeneration,
      @Param("generation") NotificationGeneration generation,
      @Param("hasChannel") boolean hasChannel,
      @Param("channel") NotificationChannel channel,
      @Param("hasAppointmentId") boolean hasAppointmentId,
      @Param("appointmentId") UUID appointmentId,
      @Param("hasFrom") boolean hasFrom,
      @Param("fromTs") Instant fromTs,
      @Param("hasTo") boolean hasTo,
      @Param("toTs") Instant toTs,
      @Param("hasEvent") boolean hasEvent,
      @Param("eventType") DeliveryEventType eventType,
      Pageable pageable);

  // Brevo ts_epoch stored as seconds → year ~58699 until V912 heals the row.
  String EVENT_AT =
      "CASE WHEN EXTRACT(EPOCH FROM e.occurred_at) >= 1000000000000"
          + " THEN to_timestamp(EXTRACT(EPOCH FROM e.occurred_at) / 1000.0)"
          + " ELSE e.occurred_at END";

  String EVENT_RANGE =
      "WITH ev AS ( SELECT healed.notification_id, healed.event_type, healed.dealership_id,"
          + " healed.occurred_at FROM ( SELECT e.notification_id, e.event_type, n.dealership_id, "
          + EVENT_AT
          + " AS occurred_at"
          + " FROM notification_delivery_events e"
          + " JOIN notifications n ON n.id = e.notification_id"
          + " WHERE n.dealership_id = :shop"
          + " AND ((e.occurred_at >= :fromTs AND e.occurred_at < :toTs)"
          + " OR EXTRACT(EPOCH FROM e.occurred_at) >= 1000000000000)"
          + " ) healed"
          + " WHERE healed.occurred_at >= :fromTs AND healed.occurred_at < :toTs"
          + ") ";

  @Query(
      value =
          EVENT_RANGE
              + """
              SELECT
                (SELECT count(*) FROM appointments a
                  WHERE a.dealership_id = :shop
                    AND a.scheduled_at >= :fromTs AND a.scheduled_at < :toTs),
                (SELECT count(*) FROM notifications n
                  WHERE n.dealership_id = :shop AND n.status = 'SENT'
                    AND n.sent_at >= :fromTs AND n.sent_at < :toTs),
                (SELECT count(DISTINCT ev.notification_id) FROM ev
                  WHERE ev.event_type = 'OPENED'
                    AND ev.occurred_at >= :fromTs AND ev.occurred_at < :toTs),
                (SELECT count(DISTINCT ev.notification_id) FROM ev
                  WHERE ev.event_type = 'SOFT_BOUNCE'
                    AND ev.occurred_at >= :fromTs AND ev.occurred_at < :toTs),
                (SELECT count(DISTINCT ev.notification_id) FROM ev
                  WHERE ev.event_type = 'HARD_BOUNCE'
                    AND ev.occurred_at >= :fromTs AND ev.occurred_at < :toTs),
                (SELECT count(*) FROM notifications n
                  WHERE n.dealership_id = :shop AND n.status = 'DEAD_LETTER'
                    AND n.updated_at >= :fromTs AND n.updated_at < :toTs),
                (SELECT count(DISTINCT ev.notification_id) FROM ev
                  WHERE ev.event_type IN ('SOFT_BOUNCE', 'HARD_BOUNCE')
                    AND ev.occurred_at >= :fromTs AND ev.occurred_at < :toTs)
              """,
      nativeQuery = true)
  List<Object[]> countTotals(
      @Param("shop") UUID shop, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

  // first event in range so daily bounce/open sum to DISTINCT totals
  @Query(
      value =
          EVENT_RANGE
              + """
SELECT date_trunc(CAST(:bucketUnit AS text), n.sent_at AT TIME ZONE d.timezone)::date,
       'sent',
       count(*)
FROM notifications n
JOIN dealerships d ON d.id = n.dealership_id
WHERE n.dealership_id = :shop
  AND n.status = 'SENT'
  AND n.sent_at >= :fromTs AND n.sent_at < :toTs
GROUP BY 1
UNION ALL
SELECT date_trunc(CAST(:bucketUnit AS text), n.updated_at AT TIME ZONE d.timezone)::date,
       'failed',
       count(*)
FROM notifications n
JOIN dealerships d ON d.id = n.dealership_id
WHERE n.dealership_id = :shop
  AND n.status = 'DEAD_LETTER'
  AND n.updated_at >= :fromTs AND n.updated_at < :toTs
GROUP BY 1
UNION ALL
SELECT date_trunc(CAST(:bucketUnit AS text), x.first_at AT TIME ZONE d.timezone)::date,
       'bounced',
       count(*)
FROM (
  SELECT ev.notification_id, ev.dealership_id, min(ev.occurred_at) AS first_at
  FROM ev
  WHERE ev.event_type IN ('SOFT_BOUNCE', 'HARD_BOUNCE')
    AND ev.occurred_at >= :fromTs AND ev.occurred_at < :toTs
  GROUP BY ev.notification_id, ev.dealership_id
) x
JOIN dealerships d ON d.id = x.dealership_id
GROUP BY 1
UNION ALL
SELECT date_trunc(CAST(:bucketUnit AS text), x.first_at AT TIME ZONE d.timezone)::date,
       'opened',
       count(*)
FROM (
  SELECT ev.notification_id, ev.dealership_id, min(ev.occurred_at) AS first_at
  FROM ev
  WHERE ev.event_type = 'OPENED'
    AND ev.occurred_at >= :fromTs AND ev.occurred_at < :toTs
  GROUP BY ev.notification_id, ev.dealership_id
) x
JOIN dealerships d ON d.id = x.dealership_id
GROUP BY 1
""",
      nativeQuery = true)
  List<Object[]> countByBucket(
      @Param("shop") UUID shop,
      @Param("bucketUnit") String bucketUnit,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs);
}
