package com.dealership.reminder;

import com.dealership.appointment.AppointmentStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {
  // JPA cannot claim FOR UPDATE SKIP LOCKED or expire by interval without loading graphs.
  private final NamedParameterJdbcTemplate jdbc;

  public ReminderRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertForAppointment(UUID appointmentId, int offsetMinutes) {
    jdbc.update(
        """
        INSERT INTO reminders (
          id, appointment_id, offset_minutes, schedule_version, scheduled_at, status,
          attempts, created_at, updated_at)
        SELECT gen_random_uuid(),
               a.id,
               :offsetMinutes,
               a.schedule_version,
               a.scheduled_at - (CAST(:offsetMinutes AS int) * interval '1 minute'),
               CASE
                 WHEN a.scheduled_at - (CAST(:offsetMinutes AS int) * interval '1 minute') <= now()
                   THEN CAST(:expired AS reminder_status)
                 ELSE CAST(:pending AS reminder_status)
               END,
               0,
               now(),
               now()
        FROM appointments a
        WHERE a.id = :appointmentId
        """,
        new MapSqlParameterSource()
            .addValue("appointmentId", appointmentId)
            .addValue("offsetMinutes", offsetMinutes)
            .addValue("expired", ReminderStatus.EXPIRED.name())
            .addValue("pending", ReminderStatus.PENDING.name()));
  }

  public void cancelUnsent(UUID appointmentId) {
    jdbc.update(
        """
        UPDATE reminders
        SET status = CAST(:cancelled AS reminder_status), updated_at = now(),
            locked_by = NULL, lease_expires_at = NULL
        WHERE appointment_id = :appointmentId
          AND status IN (CAST(:pending AS reminder_status), CAST(:retry AS reminder_status))
        """,
        new MapSqlParameterSource()
            .addValue("appointmentId", appointmentId)
            .addValue("cancelled", ReminderStatus.CANCELLED.name())
            .addValue("pending", ReminderStatus.PENDING.name())
            .addValue("retry", ReminderStatus.RETRY_SCHEDULED.name()));
  }

  public int expireClosedWindows() {
    return jdbc.update(
        """
        UPDATE reminders r
        SET status = CAST(:expired AS reminder_status), updated_at = now()
        WHERE r.status IN (CAST(:pending AS reminder_status), CAST(:retry AS reminder_status))
          AND EXISTS (
            SELECT 1 FROM appointments a
            WHERE a.id = r.appointment_id
              AND a.status = CAST(:confirmed AS appointment_status)
              AND now() >= COALESCE(
                (SELECT min(r2.scheduled_at) FROM reminders r2
                 WHERE r2.appointment_id = r.appointment_id
                   AND r2.schedule_version = r.schedule_version
                   AND r2.scheduled_at > r.scheduled_at),
                a.scheduled_at)
          )
        """,
        new MapSqlParameterSource()
            .addValue("expired", ReminderStatus.EXPIRED.name())
            .addValue("pending", ReminderStatus.PENDING.name())
            .addValue("retry", ReminderStatus.RETRY_SCHEDULED.name())
            .addValue("confirmed", AppointmentStatus.CONFIRMED.name()));
  }

  public int expireNoShows(Duration grace) {
    int appointments =
        jdbc.update(
            """
            UPDATE appointments
            SET status = CAST(:noShow AS appointment_status), updated_at = now()
            WHERE status = CAST(:confirmed AS appointment_status)
              AND now() >= scheduled_at + CAST(:grace AS interval)
            """,
            new MapSqlParameterSource()
                .addValue("noShow", AppointmentStatus.NO_SHOW_EXPIRED.name())
                .addValue("confirmed", AppointmentStatus.CONFIRMED.name())
                .addValue("grace", toPgInterval(grace)));
    jdbc.update(
        """
        UPDATE reminders r
        SET status = CAST(:cancelled AS reminder_status), updated_at = now()
        WHERE r.status IN (CAST(:pending AS reminder_status), CAST(:retry AS reminder_status))
          AND EXISTS (
            SELECT 1 FROM appointments a
            WHERE a.id = r.appointment_id
              AND a.status = CAST(:noShow AS appointment_status))
        """,
        new MapSqlParameterSource()
            .addValue("cancelled", ReminderStatus.CANCELLED.name())
            .addValue("pending", ReminderStatus.PENDING.name())
            .addValue("retry", ReminderStatus.RETRY_SCHEDULED.name())
            .addValue("noShow", AppointmentStatus.NO_SHOW_EXPIRED.name()));
    return appointments;
  }

  // notify false is still due work; MailWorker appends logs/ instead of SMTP
  public Optional<ClaimedReminder> claimDue(String workerId, Duration lease) {
    List<ClaimedReminder> rows =
        jdbc.query(
            """
            UPDATE reminders
            SET status = CAST(:processing AS reminder_status),
                locked_by = :worker,
                locked_at = now(),
                lease_expires_at = now() + CAST(:lease AS interval),
                updated_at = now()
            WHERE id = (
              SELECT r.id FROM reminders r
              JOIN appointments a ON a.id = r.appointment_id
              JOIN customers c ON c.id = a.customer_id
              JOIN dealerships d ON d.id = a.dealership_id
              WHERE r.status IN (
                  CAST(:pending AS reminder_status),
                  CAST(:retry AS reminder_status),
                  CAST(:processing AS reminder_status))
                AND a.status = CAST(:confirmed AS appointment_status)
                AND r.scheduled_at <= now()
                AND (r.next_attempt_at IS NULL OR r.next_attempt_at <= now())
                AND (r.lease_expires_at IS NULL OR r.lease_expires_at < now())
                AND now() < COALESCE(
                      (SELECT min(r2.scheduled_at) FROM reminders r2
                       WHERE r2.appointment_id = r.appointment_id
                         AND r2.schedule_version = r.schedule_version
                         AND r2.scheduled_at > r.scheduled_at),
                      a.scheduled_at)
              ORDER BY r.scheduled_at
              FOR UPDATE OF r SKIP LOCKED
              LIMIT 1
            )
            RETURNING id, appointment_id, offset_minutes, schedule_version, scheduled_at, attempts
            """,
            new MapSqlParameterSource()
                .addValue("worker", workerId)
                .addValue("lease", toPgInterval(lease))
                .addValue("processing", ReminderStatus.PROCESSING.name())
                .addValue("pending", ReminderStatus.PENDING.name())
                .addValue("retry", ReminderStatus.RETRY_SCHEDULED.name())
                .addValue("confirmed", AppointmentStatus.CONFIRMED.name()),
            (rs, i) ->
                new ClaimedReminder(
                    rs.getObject("id", UUID.class),
                    rs.getObject("appointment_id", UUID.class),
                    rs.getInt("offset_minutes"),
                    rs.getInt("schedule_version"),
                    rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getInt("attempts")));
    return rows.stream().findFirst();
  }

  public Optional<MailFacts> loadMailFacts(UUID reminderId) {
    List<MailFacts> rows =
        jdbc.query(
            """
            SELECT r.id AS reminder_id,
                   a.id AS appointment_id,
                   r.offset_minutes,
                   r.schedule_version,
                   a.scheduled_at,
                   a.display_offset,
                   d.name AS dealership_name,
                   c.contact,
                   a."notify"
            FROM reminders r
            JOIN appointments a ON a.id = r.appointment_id
            JOIN customers c ON c.id = a.customer_id
            JOIN dealerships d ON d.id = a.dealership_id
            WHERE r.id = :id
            """,
            Map.of("id", reminderId),
            (rs, i) ->
                new MailFacts(
                    rs.getObject("reminder_id", UUID.class),
                    rs.getObject("appointment_id", UUID.class),
                    rs.getInt("offset_minutes"),
                    rs.getInt("schedule_version"),
                    rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getString("display_offset"),
                    rs.getString("dealership_name"),
                    rs.getString("contact"),
                    rs.getBoolean("notify")));
    return rows.stream().findFirst();
  }

  public boolean heartbeat(UUID reminderId, String workerId, Duration lease) {
    int updated =
        jdbc.update(
            """
            UPDATE reminders
            SET locked_by = :worker,
                lease_expires_at = now() + CAST(:lease AS interval),
                locked_at = now(),
                updated_at = now()
            WHERE id = :id AND status = CAST(:processing AS reminder_status)
            """,
            new MapSqlParameterSource()
                .addValue("id", reminderId)
                .addValue("worker", workerId)
                .addValue("lease", toPgInterval(lease))
                .addValue("processing", ReminderStatus.PROCESSING.name()));
    return updated == 1;
  }

  public void markSent(UUID reminderId) {
    jdbc.update(
        """
        UPDATE reminders
        SET status = CAST(:sent AS reminder_status), locked_by = NULL, lease_expires_at = NULL,
            updated_at = now()
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", reminderId)
            .addValue("sent", ReminderStatus.SENT.name()));
  }

  public void markRetry(UUID reminderId, Instant nextAttempt, String error) {
    jdbc.update(
        """
        UPDATE reminders
        SET status = CAST(:retry AS reminder_status),
            attempts = attempts + 1,
            next_attempt_at = :next,
            last_error = :error,
            locked_by = NULL,
            lease_expires_at = NULL,
            updated_at = now()
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", reminderId)
            .addValue("retry", ReminderStatus.RETRY_SCHEDULED.name())
            .addValue("next", Timestamp.from(nextAttempt))
            .addValue("error", truncate(error)));
  }

  public void markDead(UUID reminderId, String error) {
    jdbc.update(
        """
        UPDATE reminders
        SET status = CAST(:dead AS reminder_status),
            attempts = attempts + 1,
            last_error = :error,
            locked_by = NULL,
            lease_expires_at = NULL,
            updated_at = now()
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", reminderId)
            .addValue("dead", ReminderStatus.DEAD_LETTER.name())
            .addValue("error", truncate(error)));
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

  public record ClaimedReminder(
      UUID id,
      UUID appointmentId,
      int offsetMinutes,
      int scheduleVersion,
      Instant scheduledAt,
      int attempts) {}

  public record MailFacts(
      UUID reminderId,
      UUID appointmentId,
      int offsetMinutes,
      int scheduleVersion,
      Instant scheduledAt,
      String displayOffset,
      String dealershipName,
      String contact,
      boolean notifyEnabled) {
    public String idempotencyKey() {
      return appointmentId + ":" + offsetMinutes + ":" + scheduleVersion;
    }
  }
}
