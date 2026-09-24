package com.dealership.notification;

import com.dealership.reminder.ReminderRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationLeaseRepository {
  // SKIP LOCKED + mail-* owner: same lease idea as Reminders; Manual has no Reminder row.

  private final NamedParameterJdbcTemplate jdbc;

  public NotificationLeaseRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ClaimedManual> claimRetryDue(String workerId, Duration lease, int batch) {
    return jdbc.query(
        """
        UPDATE notifications
        SET status = CAST(:processing AS notification_status),
            locked_by = :worker,
            lease_expires_at = now() + CAST(:lease AS interval),
            updated_at = now()
        WHERE id IN (
          SELECT n.id FROM notifications n
          WHERE n.generation = CAST(:manual AS notification_generation)
            AND n.status IN (
              CAST(:retry AS notification_status),
              CAST(:processing AS notification_status))
            AND (n.next_attempt_at IS NULL OR n.next_attempt_at <= now())
            AND (n.lease_expires_at IS NULL OR n.lease_expires_at < now())
          ORDER BY n.next_attempt_at NULLS FIRST, n.created_at
          FOR UPDATE SKIP LOCKED
          LIMIT :batch
        )
        RETURNING id, appointment_id, dealership_id, idempotency_key, subject, body, attempts
        """,
        new MapSqlParameterSource()
            .addValue("worker", workerId)
            .addValue("lease", toPgInterval(lease))
            .addValue("batch", batch)
            .addValue("processing", NotificationStatus.PROCESSING.name())
            .addValue("retry", NotificationStatus.RETRY_SCHEDULED.name())
            .addValue("manual", NotificationGeneration.MANUAL.name()),
        (rs, i) ->
            new ClaimedManual(
                rs.getObject("id", UUID.class),
                rs.getObject("appointment_id", UUID.class),
                rs.getObject("dealership_id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getInt("attempts")));
  }

  public boolean heartbeat(UUID notificationId, String workerId, Duration lease) {
    int updated =
        jdbc.update(
            """
            UPDATE notifications
            SET status = CAST(:processing AS notification_status),
                locked_by = :worker,
                lease_expires_at = now() + CAST(:lease AS interval),
                updated_at = now()
            WHERE id = :id
              AND generation = CAST(:manual AS notification_generation)
              AND status IN (
                CAST(:pending AS notification_status),
                CAST(:processing AS notification_status))
              AND (
                locked_by = :worker
                OR locked_by IS NULL
                OR locked_by NOT LIKE :mailLock
                OR lease_expires_at IS NULL
                OR lease_expires_at < now())
            """,
            params(notificationId, workerId, lease)
                .addValue("pending", NotificationStatus.PENDING.name()));
    return updated == 1;
  }

  public boolean markSent(UUID notificationId, String workerId) {
    int updated =
        jdbc.update(
            """
            UPDATE notifications
            SET status = CAST(:sent AS notification_status),
                sent_at = now(),
                locked_by = NULL,
                lease_expires_at = NULL,
                updated_at = now()
            WHERE id = :id
              AND generation = CAST(:manual AS notification_generation)
              AND locked_by = :worker
              AND status = CAST(:processing AS notification_status)
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at > now()
            """,
            new MapSqlParameterSource()
                .addValue("id", notificationId)
                .addValue("worker", workerId)
                .addValue("sent", NotificationStatus.SENT.name())
                .addValue("processing", NotificationStatus.PROCESSING.name())
                .addValue("manual", NotificationGeneration.MANUAL.name()));
    return updated == 1;
  }

  public boolean markRetry(
      UUID notificationId, String workerId, Instant nextAttempt, String error) {
    int updated =
        jdbc.update(
            """
            UPDATE notifications
            SET status = CAST(:retry AS notification_status),
                attempts = attempts + 1,
                next_attempt_at = :next,
                last_error = :error,
                locked_by = NULL,
                lease_expires_at = NULL,
                updated_at = now()
            WHERE id = :id
              AND generation = CAST(:manual AS notification_generation)
              AND locked_by = :worker
              AND status = CAST(:processing AS notification_status)
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at > now()
            """,
            new MapSqlParameterSource()
                .addValue("id", notificationId)
                .addValue("worker", workerId)
                .addValue("retry", NotificationStatus.RETRY_SCHEDULED.name())
                .addValue("processing", NotificationStatus.PROCESSING.name())
                .addValue("manual", NotificationGeneration.MANUAL.name())
                .addValue("next", Timestamp.from(nextAttempt))
                .addValue("error", truncate(error)));
    return updated == 1;
  }

  public boolean markDead(UUID notificationId, String workerId, String error) {
    int updated =
        jdbc.update(
            """
            UPDATE notifications
            SET status = CAST(:dead AS notification_status),
                attempts = attempts + 1,
                last_error = :error,
                locked_by = NULL,
                lease_expires_at = NULL,
                updated_at = now()
            WHERE id = :id
              AND generation = CAST(:manual AS notification_generation)
              AND locked_by = :worker
              AND status = CAST(:processing AS notification_status)
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at > now()
            """,
            new MapSqlParameterSource()
                .addValue("id", notificationId)
                .addValue("worker", workerId)
                .addValue("dead", NotificationStatus.DEAD_LETTER.name())
                .addValue("processing", NotificationStatus.PROCESSING.name())
                .addValue("manual", NotificationGeneration.MANUAL.name())
                .addValue("error", truncate(error)));
    return updated == 1;
  }

  private MapSqlParameterSource params(UUID notificationId, String workerId, Duration lease) {
    return new MapSqlParameterSource()
        .addValue("id", notificationId)
        .addValue("worker", workerId)
        .addValue("lease", toPgInterval(lease))
        .addValue("processing", NotificationStatus.PROCESSING.name())
        .addValue("manual", NotificationGeneration.MANUAL.name())
        .addValue("mailLock", ReminderRepository.MAIL_WORKER_PREFIX + "%");
  }

  private static String toPgInterval(Duration lease) {
    return lease.toSeconds() + " seconds";
  }

  private static String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= 1024 ? error : error.substring(0, 1024);
  }

  public record ClaimedManual(
      UUID id,
      UUID appointmentId,
      UUID dealershipId,
      String idempotencyKey,
      String subject,
      String body,
      int attempts) {}
}
