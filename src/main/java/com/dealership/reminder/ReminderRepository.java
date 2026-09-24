package com.dealership.reminder;

import com.dealership.appointment.AppointmentStatus;
import com.dealership.notification.NotificationGeneration;
import com.dealership.notification.NotificationStatus;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {
  // JPA cannot claim FOR UPDATE SKIP LOCKED or expire by interval without loading
  // graphs.
  // next due or visit; /2 is the Send Window midpoint (adjacent gap), not the
  // full stretch
  public static final String MAIL_WORKER_PREFIX = "mail-";
  private static final String NEXT_DUE =
      """
      COALESCE(
        (SELECT min(r2.scheduled_at) FROM reminders r2
         WHERE r2.appointment_id = r.appointment_id
           AND r2.schedule_version = r.schedule_version
           AND r2.scheduled_at > r.scheduled_at),
        a.scheduled_at)
      """;
  private static final String BEFORE_MIDPOINT =
      "now() < r.scheduled_at + ((" + NEXT_DUE.trim() + ") - r.scheduled_at) / 2";
  private static final String PAST_MIDPOINT =
      "now() >= r.scheduled_at + ((" + NEXT_DUE.trim() + ") - r.scheduled_at) / 2";

  // text blocks strip the space after AND, which glued into ANDnow
  private static String and(String predicate) {
    return " AND (" + predicate.trim() + ")";
  }

  private final NamedParameterJdbcTemplate jdbc;

  public ReminderRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertForAppointment(
      UUID appointmentId, int offsetMinutes, Integer nextOffsetMinutes, int scheduleVersion) {
    jdbc.update(
        """
        INSERT INTO reminders (
          id, appointment_id, offset_minutes, schedule_version, scheduled_at, status,
          attempts, created_at, updated_at)
        SELECT gen_random_uuid(),
               a.id,
               :offsetMinutes,
               :scheduleVersion,
               w.due,
                   -- Same midpoint as claim. Skip again only when this offset already SENT
                   -- and the new due is past (reschedule inside 24h after they got the 24h).
                   CASE
                     WHEN EXISTS (
                       SELECT 1 FROM notifications n
                       WHERE n.appointment_id = a.id
                         AND n.offset_minutes = :offsetMinutes
                         AND n.generation = CAST(:system AS notification_generation)
                         AND n.status = CAST(:sent AS notification_status))
                       AND now() >= w.due
                       THEN CAST(:expired AS reminder_status)
                     WHEN now() >= w.due + (w.next_due - w.due) / 2
                       THEN CAST(:expired AS reminder_status)
                     ELSE CAST(:pending AS reminder_status)
                   END,
               0,
               now(),
               now()
        FROM appointments a
        CROSS JOIN LATERAL (
          SELECT a.scheduled_at - (CAST(:offsetMinutes AS int) * interval '1 minute') AS due,
                 CASE
                   WHEN :nextOffsetMinutes IS NULL THEN a.scheduled_at
                   ELSE a.scheduled_at
                     - (CAST(:nextOffsetMinutes AS int) * interval '1 minute')
                 END AS next_due
        ) w
        WHERE a.id = :appointmentId
        """,
        new MapSqlParameterSource()
            .addValue("appointmentId", appointmentId)
            .addValue("offsetMinutes", offsetMinutes)
            .addValue("nextOffsetMinutes", nextOffsetMinutes, Types.INTEGER)
            .addValue("scheduleVersion", scheduleVersion)
            .addValue("expired", ReminderStatus.EXPIRED.name())
            .addValue("pending", ReminderStatus.PENDING.name())
            .addValue("system", NotificationGeneration.SYSTEM.name())
            .addValue("sent", NotificationStatus.SENT.name()));
  }

  public int nextScheduleVersion(UUID appointmentId) {
    Integer next =
        jdbc.queryForObject(
            """
            SELECT COALESCE(MAX(schedule_version), 0) + 1
            FROM reminders
            WHERE appointment_id = :appointmentId
            """,
            new MapSqlParameterSource().addValue("appointmentId", appointmentId),
            Integer.class);
    return next == null ? 1 : next;
  }

  public List<ReminderRow> listHistory(UUID appointmentId) {
    return jdbc.query(
        """
        SELECT id, offset_minutes, schedule_version, scheduled_at, status
        FROM reminders
        WHERE appointment_id = :appointmentId
        ORDER BY schedule_version DESC, offset_minutes DESC
        """,
        new MapSqlParameterSource().addValue("appointmentId", appointmentId),
        (rs, i) ->
            new ReminderRow(
                rs.getObject("id", UUID.class),
                rs.getInt("offset_minutes"),
                rs.getInt("schedule_version"),
                rs.getTimestamp("scheduled_at").toInstant(),
                ReminderStatus.valueOf(rs.getString("status"))));
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
        """
            + and(PAST_MIDPOINT)
            + """
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
  // batch from CPUs (floor 18 = 500k/day × 2 offsets on a 500ms poll); cap 50 so
  // no findAll
  public List<ClaimedReminder> claimDue(String workerId, Duration lease, int batch) {
    return jdbc.query(
        """
        UPDATE reminders
        SET status = CAST(:processing AS reminder_status),
            locked_by = :worker,
            lease_expires_at = now() + CAST(:lease AS interval),
            updated_at = now()
        WHERE id IN (
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
        """
            + and(BEFORE_MIDPOINT)
            + """
                      ORDER BY r.scheduled_at
              FOR UPDATE OF r SKIP LOCKED
              LIMIT :batch
            )
            RETURNING id, appointment_id, offset_minutes, schedule_version, scheduled_at, attempts
            """,
        new MapSqlParameterSource()
            .addValue("worker", workerId)
            .addValue("lease", toPgInterval(lease))
            .addValue("batch", batch)
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
  }

  public Optional<MailFacts> loadMailFactsForAppointment(UUID appointmentId) {
    return jdbc
        .query(
            """
            SELECT a.id AS appointment_id,
                   a.dealership_id,
                   a.scheduled_at,
                   a.display_offset,
                   d.name AS dealership_name,
                   u.name AS customer_name,
                   v.make AS vehicle_make,
                   v.model AS vehicle_model,
                   v.year AS vehicle_year,
                   v.registration_number,
                   c.contact,
                   a."notify"
            FROM appointments a
            JOIN customers c ON c.id = a.customer_id
            JOIN users u ON u.id = c.user_id
            JOIN dealerships d ON d.id = a.dealership_id
            JOIN vehicles v ON v.id = a.vehicle_id
            WHERE a.id = :id
            """,
            new MapSqlParameterSource().addValue("id", appointmentId),
            (rs, i) ->
                new MailFacts(
                    null,
                    rs.getObject("appointment_id", UUID.class),
                    rs.getObject("dealership_id", UUID.class),
                    0,
                    0,
                    rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getString("display_offset"),
                    rs.getString("dealership_name"),
                    rs.getString("customer_name"),
                    rs.getString("vehicle_make"),
                    rs.getString("vehicle_model"),
                    rs.getObject("vehicle_year", Integer.class),
                    rs.getString("registration_number"),
                    rs.getString("contact"),
                    rs.getBoolean("notify"),
                    0))
        .stream()
        .findFirst();
  }

  public Optional<MailFacts> loadMailFacts(UUID reminderId) {
    return loadMailFacts(List.of(reminderId)).stream().findFirst();
  }

  public List<MailFacts> loadMailFacts(Collection<UUID> reminderIds) {
    if (reminderIds == null || reminderIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        """
        SELECT r.id AS reminder_id,
               a.id AS appointment_id,
               a.dealership_id,
               r.offset_minutes,
               r.schedule_version,
               a.scheduled_at,
               a.display_offset,
               d.name AS dealership_name,
               u.name AS customer_name,
               v.make AS vehicle_make,
               v.model AS vehicle_model,
               v.year AS vehicle_year,
               v.registration_number,
               c.contact,
               a."notify",
               r.attempts
        FROM reminders r
        JOIN appointments a ON a.id = r.appointment_id
        JOIN customers c ON c.id = a.customer_id
        JOIN users u ON u.id = c.user_id
        JOIN dealerships d ON d.id = a.dealership_id
        JOIN vehicles v ON v.id = a.vehicle_id
        WHERE r.id IN (:ids)
        """,
        Map.of("ids", reminderIds),
        (rs, i) ->
            new MailFacts(
                rs.getObject("reminder_id", UUID.class),
                rs.getObject("appointment_id", UUID.class),
                rs.getObject("dealership_id", UUID.class),
                rs.getInt("offset_minutes"),
                rs.getInt("schedule_version"),
                rs.getTimestamp("scheduled_at").toInstant(),
                rs.getString("display_offset"),
                rs.getString("dealership_name"),
                rs.getString("customer_name"),
                rs.getString("vehicle_make"),
                rs.getString("vehicle_model"),
                rs.getObject("vehicle_year", Integer.class),
                rs.getString("registration_number"),
                rs.getString("contact"),
                rs.getBoolean("notify"),
                rs.getInt("attempts")));
  }

  public boolean reopenDead(UUID reminderId, Duration lease) {
    int updated =
        jdbc.update(
            """
            UPDATE reminders
            SET status = CAST(:processing AS reminder_status),
                attempts = 0,
                last_error = NULL,
                next_attempt_at = NULL,
                locked_by = :worker,
                lease_expires_at = now() + CAST(:lease AS interval),
                updated_at = now()
            WHERE id = :id
              AND (
                status = CAST(:dead AS reminder_status)
                OR (status = CAST(:processing AS reminder_status) AND locked_by = :worker)
              )
            """,
            new MapSqlParameterSource()
                .addValue("id", reminderId)
                .addValue("worker", "replay")
                .addValue("lease", toPgInterval(lease))
                .addValue("processing", ReminderStatus.PROCESSING.name())
                .addValue("dead", ReminderStatus.DEAD_LETTER.name()));
    return updated == 1;
  }

  public boolean heartbeat(UUID reminderId, String workerId, Duration lease) {
    // Poller/replay hold PROCESSING until SMTP starts; a live mail-* owner blocks a
    // second send.
    int updated =
        jdbc.update(
            """
            UPDATE reminders
            SET locked_by = :worker,
                lease_expires_at = now() + CAST(:lease AS interval),
                updated_at = now()
            WHERE id = :id AND status = CAST(:processing AS reminder_status)
              AND (
                locked_by = :worker
                OR locked_by IS NULL
                OR locked_by NOT LIKE :mailLock
                OR lease_expires_at IS NULL
                OR lease_expires_at < now())
            """,
            new MapSqlParameterSource()
                .addValue("id", reminderId)
                .addValue("worker", workerId)
                .addValue("lease", toPgInterval(lease))
                .addValue("mailLock", MAIL_WORKER_PREFIX + "%")
                .addValue("processing", ReminderStatus.PROCESSING.name()));
    return updated == 1;
  }

  public boolean markSent(UUID reminderId, String workerId) {
    int updated =
        jdbc.update(
            """
            UPDATE reminders
            SET status = CAST(:sent AS reminder_status), locked_by = NULL, lease_expires_at = NULL,
                updated_at = now()
            WHERE id = :id
              AND locked_by = :worker
              AND status = CAST(:processing AS reminder_status)
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at > now()
            """,
            new MapSqlParameterSource()
                .addValue("id", reminderId)
                .addValue("worker", workerId)
                .addValue("sent", ReminderStatus.SENT.name())
                .addValue("processing", ReminderStatus.PROCESSING.name()));
    return updated == 1;
  }

  public boolean markRetry(UUID reminderId, String workerId, Instant nextAttempt, String error) {
    int updated =
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
              AND locked_by = :worker
              AND status = CAST(:processing AS reminder_status)
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at > now()
            """,
            new MapSqlParameterSource()
                .addValue("id", reminderId)
                .addValue("worker", workerId)
                .addValue("retry", ReminderStatus.RETRY_SCHEDULED.name())
                .addValue("processing", ReminderStatus.PROCESSING.name())
                .addValue("next", Timestamp.from(nextAttempt))
                .addValue("error", truncate(error)));
    return updated == 1;
  }

  public boolean markDead(UUID reminderId, String workerId, String error) {
    int updated =
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
              AND locked_by = :worker
              AND status = CAST(:processing AS reminder_status)
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at > now()
            """,
            new MapSqlParameterSource()
                .addValue("id", reminderId)
                .addValue("worker", workerId)
                .addValue("dead", ReminderStatus.DEAD_LETTER.name())
                .addValue("processing", ReminderStatus.PROCESSING.name())
                .addValue("error", truncate(error)));
    return updated == 1;
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

  public record ReminderRow(
      UUID id, int offsetMinutes, int scheduleVersion, Instant dueAt, ReminderStatus status) {}

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
      UUID dealershipId,
      int offsetMinutes,
      int scheduleVersion,
      Instant scheduledAt,
      String displayOffset,
      String dealershipName,
      String customerName,
      String vehicleMake,
      String vehicleModel,
      Integer vehicleYear,
      String registrationNumber,
      String contact,
      boolean notifyEnabled,
      int attempts) {
    public String idempotencyKey() {
      return appointmentId + ":" + offsetMinutes + ":" + scheduleVersion;
    }
  }
}
