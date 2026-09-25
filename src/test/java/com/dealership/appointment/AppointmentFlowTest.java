package com.dealership.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.identity.AuthDtos;
import com.dealership.identity.Role;
import com.dealership.notification.FileNotificationLog;
import com.dealership.notification.NotificationService;
import com.dealership.notification.OutboxPublisher;
import com.dealership.notification.smtp.StubNotificationSender;
import com.dealership.reminder.ReminderRepository;
import com.dealership.reminder.ReminderScheduler;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class AppointmentFlowTest extends AbstractIT {

  @Autowired JdbcTemplate jdbc;

  @Autowired StubNotificationSender stub;

  @Autowired ReminderScheduler poller;

  @Autowired ReminderRepository reminderRows;

  @Autowired OutboxPublisher publisher;

  @Autowired NotificationService notifications;

  @Autowired FileNotificationLog notifyOffLog;

  @Autowired IdempotencyService idempotency;

  @Autowired EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void clearStub() {
    stub.clear();
    notifyOffLog.clear();
  }

  @Test
  void createRemindersIdempotencyAndOneConfirmedPerVehicle() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);

    String customerEmail = "cust-" + UUID.randomUUID() + "@ex.com";
    String customerToken = registerAndLogin(customerEmail, Role.CUSTOMER);
    String plate1 = randomPlate("KA");
    String plate2 = randomPlate("MH");
    UUID vehicleId = createVehicle(customerToken, plate1);
    UUID vehicle2 = createVehicle(customerToken, plate2);

    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    String body =
        """
        {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":true}
        """
            .formatted(vehicleId, dealershipId, when);

    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    ResponseEntity<AppointmentDtos.AppointmentResponse> created =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    AppointmentDtos.AppointmentResponse appointment = created.getBody();
    assertEquals("+05:30", appointment.displayOffset());
    assertTrue(appointment.scheduledAtLocal().contains("+05:30"));

    Integer reminderCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM reminders WHERE appointment_id = ?",
            Integer.class,
            appointment.id());
    assertEquals(2, reminderCount);
    // Visit 3 days out → both offsets due in the future → PENDING (normal send path).
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reminders
            WHERE appointment_id = ? AND status = 'PENDING'
              AND offset_minutes IN (1440, 120)
            """,
            Integer.class,
            appointment.id()));

    ResponseEntity<AppointmentDtos.AppointmentResponse> replay =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(appointment.id(), replay.getBody().id());
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            "SELECT count(*) FROM reminders WHERE appointment_id = ?",
            Integer.class,
            appointment.id()));

    HttpHeaders otherKey = bearer(customerToken);
    otherKey.add("Idempotency-Key", "key-" + UUID.randomUUID());
    ResponseEntity<String> conflict =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body, otherKey),
            String.class);
    assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    assertTrue(conflict.getBody().contains("VEHICLE_ALREADY_CONFIRMED"));

    OffsetDateTime when2 = when.plusDays(1);
    String body2 =
        """
        {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
        """
            .formatted(vehicle2, dealershipId, when2);
    HttpHeaders key2 = bearer(customerToken);
    key2.add("Idempotency-Key", "key-" + UUID.randomUUID());
    ResponseEntity<AppointmentDtos.AppointmentResponse> second =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body2, key2),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, second.getStatusCode());
    assertNotEquals(appointment.id(), second.getBody().id());
  }

  @Test
  void staffReadsRemindersNotScheduledUntilDue() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    ResponseEntity<String> asCustomer =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/reminders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(customerToken)),
            String.class);
    assertEquals(HttpStatus.FORBIDDEN, asCustomer.getStatusCode());

    ResponseEntity<List> listed =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/reminders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            List.class);
    assertEquals(HttpStatus.OK, listed.getStatusCode());
    assertEquals(2, listed.getBody().size());
    assertTrue(listed.getBody().toString().contains("NOT_SCHEDULED"));
    assertTrue(listed.getBody().toString().contains("offsetMinutes"));
    assertTrue(listed.getBody().toString().contains("scheduleVersion"));
  }

  @Test
  void duplicateReminderOffsetSameVersionFailsUniqueConstraint() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    assertThrows(
        DuplicateKeyException.class,
        () ->
            jdbc.update(
                """
                INSERT INTO reminders (
                  id, appointment_id, offset_minutes, schedule_version, scheduled_at, status,
                  attempts, created_at, updated_at)
                SELECT gen_random_uuid(), appointment_id, offset_minutes, schedule_version,
                       scheduled_at, status, 0, now(), now()
                FROM reminders
                WHERE appointment_id = ?
                LIMIT 1
                """,
                appointmentId));
  }

  @Test
  void markSentRequiresLiveProcessingLease() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    UUID reminderId =
        jdbc.queryForObject(
            "SELECT id FROM reminders WHERE appointment_id = ? LIMIT 1", UUID.class, appointmentId);
    jdbc.update(
        """
        UPDATE reminders
        SET status = 'PROCESSING', lease_expires_at = now() - interval '1 second'
        WHERE id = ?
        """,
        reminderId);
    assertFalse(reminderRows.markSent(reminderId, "mail-test"));
  }

  @Test
  void replayOutsideHomeShopIsNotFound() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String otherStaff =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    createDealership(otherStaff);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    UUID reminderId =
        jdbc.queryForObject(
            "SELECT id FROM reminders WHERE appointment_id = ? LIMIT 1", UUID.class, appointmentId);
    UUID notificationId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO notifications (
          id, reminder_id, appointment_id, dealership_id, offset_minutes,
          channel, generation, idempotency_key, status, attempts, created_at, updated_at)
        SELECT ?, ?, a.id, a.dealership_id, 1440,
          'EMAIL'::notification_channel, 'SYSTEM'::notification_generation,
          ?, CAST('DEAD_LETTER' AS notification_status), 0, now(), now()
        FROM appointments a
        WHERE a.id = ?
        """,
        notificationId,
        reminderId,
        "replay-" + notificationId,
        appointmentId);
    ResponseEntity<String> replay =
        http.exchange(
            "/api/v1/notifications/" + notificationId + "/replay",
            HttpMethod.POST,
            new HttpEntity<>(bearer(otherStaff)),
            String.class);
    assertEquals(HttpStatus.NOT_FOUND, replay.getStatusCode());
  }

  @Test
  void replayPendingIsConflict() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    UUID reminderId =
        jdbc.queryForObject(
            "SELECT id FROM reminders WHERE appointment_id = ? LIMIT 1", UUID.class, appointmentId);
    UUID notificationId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO notifications (
          id, reminder_id, appointment_id, dealership_id, offset_minutes,
          channel, generation, idempotency_key, status, attempts, created_at, updated_at)
        SELECT ?, ?, a.id, a.dealership_id, 1440,
          'EMAIL'::notification_channel, 'SYSTEM'::notification_generation,
          ?, CAST('PENDING' AS notification_status), 0, now(), now()
        FROM appointments a
        WHERE a.id = ?
        """,
        notificationId,
        reminderId,
        "pending-" + notificationId,
        appointmentId);
    ResponseEntity<String> replay =
        http.exchange(
            "/api/v1/notifications/" + notificationId + "/replay",
            HttpMethod.POST,
            new HttpEntity<>(bearer(staffToken)),
            String.class);
    assertEquals(HttpStatus.CONFLICT, replay.getStatusCode());
    assertTrue(replay.getBody().contains("REPLAY_NOT_DEAD_LETTER"));
  }

  @Test
  void replayDeadLetterSendsOnceWithSameKey() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    UUID reminderId =
        jdbc.queryForObject(
            """
            SELECT id FROM reminders
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            UUID.class,
            appointmentId);
    jdbc.update(
        "UPDATE reminders SET status = CAST('DEAD_LETTER' AS reminder_status) WHERE id = ?",
        reminderId);
    UUID notificationId = UUID.randomUUID();
    String key = appointmentId + ":1440:1";
    jdbc.update(
        """
        INSERT INTO notifications (
          id, reminder_id, appointment_id, dealership_id, offset_minutes,
          channel, generation, idempotency_key, status,
          attempts, last_error, created_at, updated_at)
        SELECT ?, ?, a.id, a.dealership_id, 1440,
          'EMAIL'::notification_channel, 'SYSTEM'::notification_generation,
          ?, CAST('DEAD_LETTER' AS notification_status), 5, 'smtp failed', now(), now()
        FROM appointments a
        WHERE a.id = ?
        """,
        notificationId,
        reminderId,
        key,
        appointmentId);
    ResponseEntity<String> replay =
        http.exchange(
            "/api/v1/notifications/" + notificationId + "/replay",
            HttpMethod.POST,
            new HttpEntity<>(bearer(staffToken)),
            String.class);
    assertEquals(HttpStatus.ACCEPTED, replay.getStatusCode());
    publisher.drain();
    long deadline = System.currentTimeMillis() + 5000;
    long stubSends = 0;
    while (System.currentTimeMillis() < deadline) {
      stubSends =
          stub.recorded().stream()
              .filter(
                  s -> s.appointmentId().equals(appointmentId) && key.equals(s.idempotencyKey()))
              .count();
      if (stubSends >= 1) {
        break;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    assertEquals(1, stubSends);
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            """
            SELECT count(*) FROM notifications
            WHERE id = ? AND status = 'SENT' AND idempotency_key = ?
            """,
            Integer.class,
            notificationId,
            key));
    assertEquals(
        "SENT",
        jdbc.queryForObject(
            "SELECT status::text FROM reminders WHERE id = ?", String.class, reminderId));
  }

  @Test
  void staffWithoutHomeShopCannotListCustomers() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    ResponseEntity<String> listed =
        http.exchange(
            "/api/v1/customers",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            String.class);
    assertEquals(HttpStatus.NOT_FOUND, listed.getStatusCode());
  }

  @Test
  void idempotencyKeyIsScopedToUser() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String firstToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    String secondToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID firstVehicle = createVehicle(firstToken, randomPlate("KA"));
    UUID secondVehicle = createVehicle(secondToken, randomPlate("MH"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    String sharedKey = "shared-" + UUID.randomUUID();
    HttpHeaders first = bearer(firstToken);
    first.add("Idempotency-Key", sharedKey);
    UUID firstAppointment =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(firstVehicle, dealershipId, when),
                    first),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    HttpHeaders second = bearer(secondToken);
    second.add("Idempotency-Key", sharedKey);
    ResponseEntity<AppointmentDtos.AppointmentResponse> created =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                """
                    .formatted(secondVehicle, dealershipId, when),
                second),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertNotEquals(firstAppointment, created.getBody().id());
  }

  @Test
  void tenHoursOutExpiresTwentyFourHourRowInSql() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("DL"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(10)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    String body =
        """
        {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
        """
            .formatted(vehicleId, dealershipId, when);
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID id =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    String twentyFour =
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 1440",
            String.class,
            id);
    String twoHour =
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 120",
            String.class,
            id);
    assertEquals("EXPIRED", twentyFour);
    assertEquals("PENDING", twoHour);

    Integer intervalMatch =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reminders r
            JOIN appointments a ON a.id = r.appointment_id
            WHERE r.appointment_id = ?
                  AND r.offset_minutes = 120
              AND r.scheduled_at = a.scheduled_at - interval '2 hours'
            """,
            Integer.class,
            id);
    assertEquals(1, intervalMatch);
  }

  @Test
  void moreThanTwentyFourHoursOutKeepsBothOffsetsPending() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("DL"));
    // 30h out: 24h due is still ~6h in the future → must stay PENDING (regular create/send).
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(30)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID id =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":true}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 1440",
            String.class,
            id));
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 120",
            String.class,
            id));
  }

  @Test
  void twentyHoursOutKeepsTwentyFourHourPendingBeforeMidpoint() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("DL"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(20)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID id =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    // Visit in 20h → 24h due is past, still before midpoint T−13h, never SENT → PENDING.
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 1440",
            String.class,
            id));
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 120",
            String.class,
            id));
  }

  @Test
  void rescheduleRejectsSameInstantAndPastVisit() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("DL"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(2)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    var created =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody();
    ResponseEntity<String> same =
        http.exchange(
            "/api/v1/appointments/" + created.id() + "/reschedule",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"scheduledAt\":\"" + created.scheduledAtLocal() + "\"}", bearer(customerToken)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, same.getStatusCode());
    assertTrue(same.getBody().contains("SCHEDULED_AT_UNCHANGED"));

    OffsetDateTime withinDay =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(12)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    ResponseEntity<AppointmentDtos.AppointmentResponse> moved =
        http.exchange(
            "/api/v1/appointments/" + created.id() + "/reschedule",
            HttpMethod.POST,
            new HttpEntity<>("{\"scheduledAt\":\"" + withinDay + "\"}", bearer(customerToken)),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.OK, moved.getStatusCode());
    assertEquals(
        "EXPIRED",
        jdbc.queryForObject(
            """
            SELECT status FROM reminders
            WHERE appointment_id = ? AND schedule_version = 2 AND offset_minutes = 1440
            """,
            String.class,
            created.id()));
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            """
            SELECT status FROM reminders
            WHERE appointment_id = ? AND schedule_version = 2 AND offset_minutes = 120
            """,
            String.class,
            created.id()));

    ResponseEntity<List> history =
        http.exchange(
            "/api/v1/appointments/" + created.id() + "/reminders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            List.class);
    assertEquals(HttpStatus.OK, history.getStatusCode());
    assertEquals(4, history.getBody().size());
    assertTrue(history.getBody().toString().contains("scheduleVersion"));
  }

  @Test
  void rescheduleInsideTwentyFourHoursSkipsAfterSent() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("DL"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(30)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID id =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    UUID reminderId =
        jdbc.queryForObject(
            """
            SELECT id FROM reminders
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            UUID.class,
            id);
    jdbc.update(
        """
        INSERT INTO notifications (
          id, reminder_id, appointment_id, dealership_id, offset_minutes,
          channel, generation, idempotency_key, status, attempts, sent_at,
          created_at, updated_at)
        SELECT ?, ?, a.id, a.dealership_id, 1440,
          'EMAIL'::notification_channel, 'SYSTEM'::notification_generation,
          ?, CAST('SENT' AS notification_status), 1, now(), now(), now()
        FROM appointments a
        WHERE a.id = ?
        """,
        UUID.randomUUID(),
        reminderId,
        id + ":1440:1",
        id);
    OffsetDateTime closer =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(20)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    ResponseEntity<AppointmentDtos.AppointmentResponse> moved =
        http.exchange(
            "/api/v1/appointments/" + id + "/reschedule",
            HttpMethod.POST,
            new HttpEntity<>("{\"scheduledAt\":\"" + closer + "\"}", bearer(customerToken)),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.OK, moved.getStatusCode());
    assertEquals(
        "EXPIRED",
        jdbc.queryForObject(
            """
            SELECT status FROM reminders
            WHERE appointment_id = ? AND schedule_version = 2 AND offset_minutes = 1440
            """,
            String.class,
            id));
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            """
            SELECT status FROM reminders
            WHERE appointment_id = ? AND schedule_version = 2 AND offset_minutes = 120
            """,
            String.class,
            id));
  }

  @Test
  void notifyOffLogOnlyWhenInsideMidpointWindow() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID insideId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    jdbc.update(
        """
        UPDATE reminders SET scheduled_at = now() - interval '1 minute'
        WHERE appointment_id = ? AND offset_minutes = 1440
        """,
        insideId);
    assertTrue(waitForNotifyOff(insideId, 1));
    assertEquals(
        1,
        notifyOffLog.recorded().stream().filter(s -> s.appointmentId().equals(insideId)).count());
    String log = Files.readString(notifyOffLog.file(), StandardCharsets.UTF_8);
    assertTrue(log.contains("appointment_id=" + insideId));
    assertTrue(log.contains("notify=false"));
    assertEquals(
        1440,
        notifyOffLog.recorded().stream()
            .filter(s -> s.appointmentId().equals(insideId))
            .findFirst()
            .orElseThrow()
            .offsetMinutes());

    UUID vehicle2 = createVehicle(customerToken, randomPlate("MH"));
    HttpHeaders lateKey = bearer(customerToken);
    lateKey.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID pastId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                    """
                        .formatted(vehicle2, dealershipId, when.plusDays(1)),
                    lateKey),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();
    jdbc.update(
        """
        UPDATE reminders SET scheduled_at = now() - interval '12 hours'
        WHERE appointment_id = ? AND offset_minutes = 1440
        """,
        pastId);
    jdbc.update(
        """
        UPDATE reminders SET scheduled_at = now() + interval '10 hours'
        WHERE appointment_id = ? AND offset_minutes = 120
        """,
        pastId);
    notifyOffLog.clear();
    poller.tick();
    publisher.drain();
    Thread.sleep(500);
    poller.tick();
    publisher.drain();
    Thread.sleep(300);
    assertEquals(
        0, notifyOffLog.recorded().stream().filter(s -> s.appointmentId().equals(pastId)).count());
    assertEquals(
        "EXPIRED",
        jdbc.queryForObject(
            "SELECT status FROM reminders WHERE appointment_id = ? AND offset_minutes = 1440",
            String.class,
            pastId));
    String after = Files.readString(notifyOffLog.file(), StandardCharsets.UTF_8);
    assertFalse(after.contains("appointment_id=" + pastId));
  }

  private boolean waitForNotifyOff(UUID appointmentId, int min) {
    long deadline = System.currentTimeMillis() + 8000;
    while (System.currentTimeMillis() < deadline) {
      poller.tick();
      publisher.drain();
      long count =
          notifyOffLog.recorded().stream()
              .filter(s -> s.appointmentId().equals(appointmentId))
              .count();
      if (count >= min) {
        return true;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  @Test
  void onePollClaimsABatchOfDueReminders() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    UUID first =
        book(customerToken, createVehicle(customerToken, randomPlate("TN")), dealershipId, when);
    UUID second =
        book(
            customerToken,
            createVehicle(customerToken, randomPlate("MH")),
            dealershipId,
            when.plusHours(1));
    jdbc.update(
        """
        UPDATE reminders SET scheduled_at = now() - interval '1 minute'
        WHERE offset_minutes = 120 AND appointment_id IN (?, ?)
        """,
        first,
        second);

    poller.tick();
    Integer claimed =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reminders
            WHERE offset_minutes = 120 AND appointment_id IN (?, ?) AND status = 'PROCESSING'
            """,
            Integer.class,
            first,
            second);
    assertEquals(2, claimed);
    Integer outbox =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM outbox_events WHERE aggregate_id IN (
              SELECT id FROM reminders WHERE offset_minutes = 120 AND appointment_id IN (?, ?))
            """,
            Integer.class,
            first,
            second);
    assertEquals(2, outbox);
  }

  private UUID book(String token, UUID vehicleId, UUID dealershipId, OffsetDateTime when) {
    HttpHeaders headers = bearer(token);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    return http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                """
                    .formatted(vehicleId, dealershipId, when),
                headers),
            AppointmentDtos.AppointmentResponse.class)
        .getBody()
        .id();
  }

  @Test
  void concurrentClaimsSendOnce() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("TN"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    String body =
        """
        {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
        """
            .formatted(vehicleId, dealershipId, when);
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    jdbc.update(
        """
        UPDATE reminders
        SET scheduled_at = now() - interval '1 minute',
            status = CAST('PENDING' AS reminder_status),
            locked_by = NULL,
            lease_expires_at = NULL,
            next_attempt_at = NULL
        WHERE appointment_id = ? AND offset_minutes = 1440
        """,
        appointmentId);

    Thread t1 = new Thread(poller::tick);
    Thread t2 = new Thread(poller::tick);
    t1.start();
    t2.start();
    t1.join();
    t2.join();
    poller.tick();
    publisher.drain();
    long deadline = System.currentTimeMillis() + 5000;
    long stubSends = 0;
    Integer outbox = 0;
    while (System.currentTimeMillis() < deadline) {
      outbox =
          jdbc.queryForObject(
              "SELECT count(*) FROM outbox_events WHERE aggregate_id IN (SELECT id FROM reminders"
                  + " WHERE appointment_id = ? AND offset_minutes = 1440)",
              Integer.class,
              appointmentId);
      stubSends =
          stub.recorded().stream().filter(s -> s.appointmentId().equals(appointmentId)).count();
      if (outbox != null && outbox >= 1 && stubSends >= 1) {
        break;
      }
      publisher.drain();
      Thread.sleep(50);
    }

    assertEquals(1, outbox);
    assertEquals(1, stubSends);
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            """
            SELECT count(*) FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440 AND status = 'SENT'
            """,
            Integer.class,
            appointmentId));
  }

  @Test
  void crashAfterProviderAcceptBeforeSentRetriesSameKeyOnly() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("GJ"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    jdbc.update(
        """
        UPDATE reminders
        SET scheduled_at = now() - interval '1 minute',
            status = CAST('PENDING' AS reminder_status),
            locked_by = NULL,
            lease_expires_at = NULL,
            next_attempt_at = NULL
        WHERE appointment_id = ? AND offset_minutes = 1440
        """,
        appointmentId);

    poller.tick();
    publisher.drain();
    awaitStubSends(appointmentId, 1);

    String key =
        jdbc.queryForObject(
            """
            SELECT idempotency_key FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            String.class,
            appointmentId);
    UUID reminderId =
        jdbc.queryForObject(
            """
            SELECT id FROM reminders
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            UUID.class,
            appointmentId);

    UUID notificationId =
        jdbc.queryForObject(
            """
            SELECT id FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            UUID.class,
            appointmentId);
    // Provider accepted; process died before durable SENT — reopen ledger, same key.
    jdbc.update(
        """
        UPDATE notifications
        SET status = CAST('PENDING' AS notification_status),
            sent_at = NULL,
            updated_at = now()
        WHERE appointment_id = ? AND offset_minutes = 1440
        """,
        appointmentId);
    jdbc.update(
        """
        UPDATE reminders
        SET status = CAST('PROCESSING' AS reminder_status),
            locked_by = NULL,
            lease_expires_at = now() - interval '3 minutes',
            next_attempt_at = NULL,
            updated_at = now()
        WHERE id = ?
        """,
        reminderId);

    HttpHeaders hook = new HttpHeaders();
    hook.set(HttpHeaders.AUTHORIZATION, "Bearer test-webhook-secret");
    hook.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    assertEquals(
        HttpStatus.OK,
        http.exchange(
                "/api/v1/webhooks/delivery/brevo",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
{"event":"request","X-Mailin-custom":"%s","message-id":"m-crash","ts_epoch":1700000000}
"""
                        .formatted(notificationId),
                    hook),
                Void.class)
            .getStatusCode());

    poller.tick();
    publisher.drain();

    long stubSends =
        stub.recorded().stream()
            .filter(s -> s.appointmentId().equals(appointmentId) && key.equals(s.idempotencyKey()))
            .count();
    assertEquals(1, stubSends, "webhook accept must not send again");
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            """
            SELECT count(*) FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            Integer.class,
            appointmentId));
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            """
            SELECT count(DISTINCT idempotency_key) FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            Integer.class,
            appointmentId));
    assertEquals(
        key,
        jdbc.queryForObject(
            """
            SELECT idempotency_key FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            String.class,
            appointmentId));
    assertEquals(
        "SENT",
        jdbc.queryForObject(
            """
            SELECT status::text FROM notifications
            WHERE appointment_id = ? AND offset_minutes = 1440
            """,
            String.class,
            appointmentId));
  }

  private void awaitStubSends(UUID appointmentId, int min) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      long stubSends =
          stub.recorded().stream().filter(s -> s.appointmentId().equals(appointmentId)).count();
      if (stubSends >= min) {
        return;
      }
      publisher.drain();
      Thread.sleep(50);
    }
    long stubSends =
        stub.recorded().stream().filter(s -> s.appointmentId().equals(appointmentId)).count();
    assertTrue(stubSends >= min, "expected >= " + min + " stub sends, got " + stubSends);
  }

  @Test
  void staffSearchesCustomerAndVehiclesForBookingIds() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    createDealership(staffToken);
    String customerEmail = "cust-" + UUID.randomUUID() + "@ex.com";
    String customerToken = registerAndLogin(customerEmail, Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));

    AuthDtos.UserResponse me = me(customerToken);

    ResponseEntity<String> forbidden =
        http.exchange(
            "/api/v1/customers?q=" + customerEmail,
            HttpMethod.GET,
            new HttpEntity<>(bearer(customerToken)),
            String.class);
    assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    assertTrue(forbidden.getBody().contains("\"success\":false"));
    assertTrue(forbidden.getBody().contains("FORBIDDEN"));

    ResponseEntity<Map> listed =
        http.exchange(
            "/api/v1/customers?q=" + customerEmail.split("@")[0],
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, listed.getStatusCode());
    assertTrue(listed.getBody().toString().contains(me.customerId().toString()));
    assertTrue(listed.getBody().toString().contains(vehicleId.toString()));

    ResponseEntity<Map> vehicles =
        http.exchange(
            "/api/v1/customers/" + me.customerId() + "/vehicles",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, vehicles.getStatusCode());
    assertTrue(vehicles.getBody().toString().contains(vehicleId.toString()));
  }

  @Test
  void staffCreatesWalkInCustomerVehicleAndBooks() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String walkInEmail = "walkin-" + UUID.randomUUID() + "@ex.com";
    String plate = randomPlate("KA");

    ResponseEntity<Map> createdCustomer =
        http.exchange(
            "/api/v1/customers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("email", walkInEmail, "name", "Walk In", "password", "password1"),
                bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.CREATED, createdCustomer.getStatusCode());
    assertEquals("Walk In", createdCustomer.getBody().get("name"));
    UUID customerId = UUID.fromString(createdCustomer.getBody().get("id").toString());

    ResponseEntity<Map> createdVehicle =
        http.exchange(
            "/api/v1/customers/" + customerId + "/vehicles",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("registrationNumber", plate, "make", "Honda", "model", "City", "year", 2021),
                bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.CREATED, createdVehicle.getStatusCode());
    UUID vehicleId = UUID.fromString(createdVehicle.getBody().get("id").toString());
    assertEquals(customerId.toString(), createdVehicle.getBody().get("customerId").toString());

    ResponseEntity<Map> byPlate =
        http.exchange(
            "/api/v1/customers?q=" + plate,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, byPlate.getStatusCode());
    assertTrue(byPlate.getBody().toString().contains(customerId.toString()));
    assertTrue(byPlate.getBody().toString().contains(vehicleId.toString()));

    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders book = bearer(staffToken);
    book.add("Idempotency-Key", "key-" + UUID.randomUUID());
    ResponseEntity<AppointmentDtos.AppointmentResponse> booked =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"customerId":"%s","vehicleId":"%s","scheduledAt":"%s"}
                """
                    .formatted(customerId, vehicleId, when),
                book),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, booked.getStatusCode());
    assertEquals(dealershipId, booked.getBody().dealershipId());
    assertEquals(customerId, booked.getBody().customerId());
    assertEquals(vehicleId, booked.getBody().vehicleId());
    assertEquals(customerId, booked.getBody().customer().id());
    assertEquals("Walk In", booked.getBody().customer().name());
    assertEquals("Walk In", booked.getBody().vehicle().customer().name());
    assertEquals(vehicleId, booked.getBody().vehicle().id());
    assertEquals("Honda", booked.getBody().vehicle().make());
    assertEquals(dealershipId, booked.getBody().dealership().id());

    AuthDtos.TokenResponse login =
        http.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", walkInEmail, "password", "password1"),
                AuthDtos.TokenResponse.class)
            .getBody();
    ResponseEntity<AppointmentDtos.AppointmentResponse> own =
        http.exchange(
            "/api/v1/appointments/" + booked.getBody().id(),
            HttpMethod.GET,
            new HttpEntity<>(bearer(login.accessToken())),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.OK, own.getStatusCode());
  }

  @Test
  void staffNotificationListManualAndWebhook() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    String plate = randomPlate("KA");
    UUID vehicleId = createVehicle(customerToken, plate);
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    headers),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    HttpHeaders sendHeaders = bearer(staffToken);
    sendHeaders.add("Idempotency-Key", "manual-" + UUID.randomUUID());
    ResponseEntity<Map> sent =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/notifications",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"subject":"Visit us again","body":"Thanks for coming in."}
                """,
                sendHeaders),
            Map.class);
    assertEquals(HttpStatus.ACCEPTED, sent.getStatusCode());
    UUID notificationId = UUID.fromString(sent.getBody().get("id").toString());
    assertEquals("MANUAL", sent.getBody().get("generation").toString());

    publisher.drain();
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline
        && stub.recorded().stream().noneMatch(s -> appointmentId.equals(s.appointmentId()))) {
      publisher.drain();
      Thread.sleep(50);
    }
    assertTrue(stub.recorded().stream().anyMatch(s -> appointmentId.equals(s.appointmentId())));

    HttpHeaders hook = new HttpHeaders();
    hook.set(HttpHeaders.AUTHORIZATION, "Bearer test-webhook-secret");
    hook.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    ResponseEntity<Void> first =
        http.exchange(
            "/api/v1/webhooks/delivery/stub",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"event":"OPENED","correlationKey":"%s","providerEventId":"e1"}
                """
                    .formatted(notificationId),
                hook),
            Void.class);
    assertEquals(HttpStatus.OK, first.getStatusCode());
    ResponseEntity<Void> dup =
        http.exchange(
            "/api/v1/webhooks/delivery/stub",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"event":"OPENED","correlationKey":"%s","providerEventId":"e1"}
                """
                    .formatted(notificationId),
                hook),
            Void.class);
    assertEquals(HttpStatus.OK, dup.getStatusCode());
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_delivery_events WHERE notification_id = ?",
            Integer.class,
            notificationId));
    String longId = "m".repeat(300);
    ResponseEntity<Void> longHook =
        http.exchange(
            "/api/v1/webhooks/delivery/stub",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"event":"OPENED","correlationKey":"%s","providerEventId":"%s"}
                """
                    .formatted(notificationId, longId),
                hook),
            Void.class);
    assertEquals(HttpStatus.OK, longHook.getStatusCode());
    ResponseEntity<Void> earlierOpen =
        http.exchange(
            "/api/v1/webhooks/delivery/stub",
            HttpMethod.POST,
            new HttpEntity<>(
                """
{"event":"OPENED","correlationKey":"%s","providerEventId":"e-day2","occurredAt":"2026-01-02T00:00:00Z"}
"""
                    .formatted(notificationId),
                hook),
            Void.class);
    assertEquals(HttpStatus.OK, earlierOpen.getStatusCode());
    assertEquals(
        Integer.valueOf(3),
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_delivery_events WHERE notification_id = ?",
            Integer.class,
            notificationId));
    assertEquals(
        Integer.valueOf(64),
        jdbc.queryForObject(
            """
SELECT length(provider_event_id) FROM notification_delivery_events
WHERE notification_id = ? AND provider_event_id <> 'e1' AND provider_event_id <> 'e-day2'
""",
            Integer.class,
            notificationId));
    assertEquals(
        "SENT",
        jdbc.queryForObject(
            "SELECT status FROM notifications WHERE id = ?", String.class, notificationId));

    Instant from = Instant.now().minusSeconds(3600);
    Instant to = Instant.now().plusSeconds(3600);
    ResponseEntity<Map> listed =
        http.exchange(
            "/api/v1/notifications?generation=MANUAL&hasEvent=OPENED&from=" + from + "&to=" + to,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, listed.getStatusCode());
    assertTrue(listed.getBody().toString().contains(notificationId.toString()));
    assertTrue(listed.getBody().toString().contains(plate));

    ResponseEntity<Map> detail =
        http.exchange(
            "/api/v1/notifications/" + notificationId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    Map appointment = (Map) detail.getBody().get("appointment");
    assertNotNull(appointment);
    assertEquals(appointmentId.toString(), appointment.get("id").toString());
    assertEquals(plate, ((Map) appointment.get("vehicle")).get("registrationNumber"));

    ResponseEntity<Map> stats =
        http.exchange(
            "/api/v1/notifications/stats?from=" + from + "&to=" + to,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, stats.getStatusCode());
    assertTrue(((Number) stats.getBody().get("opened")).intValue() >= 1);
    assertTrue(stats.getBody().containsKey("failed"));
    assertTrue(stats.getBody().containsKey("bounced"));

    ResponseEntity<Void> blocked =
        http.exchange(
            "/api/v1/webhooks/delivery/stub",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"event":"BLOCKED","correlationKey":"%s","providerEventId":"blocked-1"}
                """
                    .formatted(notificationId),
                hook),
            Void.class);
    assertEquals(HttpStatus.OK, blocked.getStatusCode());
    ResponseEntity<Map> bouncedStats =
        http.exchange(
            "/api/v1/notifications/stats?from=" + from + "&to=" + to,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, bouncedStats.getStatusCode());
    assertTrue(((Number) bouncedStats.getBody().get("bounced")).intValue() >= 1);
    ResponseEntity<Map> bouncedDetail =
        http.exchange(
            "/api/v1/notifications/" + notificationId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(Boolean.TRUE, bouncedDetail.getBody().get("bounced"));

    Instant openedFrom = Instant.parse("2026-01-01T00:00:00Z");
    Instant openedTo = Instant.now().plusSeconds(3600);
    ResponseEntity<Map> openedBuckets =
        http.exchange(
            "/api/v1/notifications/stats?from=" + openedFrom + "&to=" + openedTo + "&bucket=DAY",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, openedBuckets.getStatusCode());
    long openedTotal = ((Number) openedBuckets.getBody().get("opened")).longValue();
    assertEquals(1, openedTotal);
    long openedSum = 0;
    for (Object row : (List<?>) openedBuckets.getBody().get("buckets")) {
      openedSum += ((Number) ((Map<?, ?>) row).get("opened")).longValue();
    }
    assertEquals(openedTotal, openedSum);

    Instant visitFrom = when.toInstant().minusSeconds(60);
    Instant visitTo = when.toInstant().plusSeconds(3600);
    ResponseEntity<Map> apptStats =
        http.exchange(
            "/api/v1/appointments/stats?from=" + visitFrom + "&to=" + visitTo,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, apptStats.getStatusCode());
    assertTrue(((Number) apptStats.getBody().get("confirmed")).intValue() >= 1);

    ResponseEntity<Map> dash =
        http.exchange(
            "/api/v1/dashboard/stats?from=" + from + "&to=" + visitTo + "&bucket=DAY",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, dash.getStatusCode());
    @SuppressWarnings("unchecked")
    Map<String, Object> appts = (Map<String, Object>) dash.getBody().get("appointments");
    assertTrue(((Number) appts.get("confirmed")).intValue() >= 1);
    assertTrue(appts.get("buckets") instanceof List);
    assertFalse(((List<?>) appts.get("buckets")).isEmpty());
    @SuppressWarnings("unchecked")
    Map<String, Object> mail = (Map<String, Object>) dash.getBody().get("notifications");
    assertTrue(((Number) mail.get("opened")).intValue() >= 1);

    jdbc.update(
        """
        UPDATE notification_delivery_events
        SET occurred_at = to_timestamp(EXTRACT(EPOCH FROM occurred_at) * 1000.0)
        WHERE notification_id = ? AND event_type = 'OPENED'
        """,
        notificationId);
    ResponseEntity<Map> millisStats =
        http.exchange(
            "/api/v1/notifications/stats?from=" + from + "&to=" + to,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, millisStats.getStatusCode());
    assertTrue(((Number) millisStats.getBody().get("opened")).intValue() >= 1);

    ResponseEntity<Map> byVisit =
        http.exchange(
            "/api/v1/notifications?appointmentId=" + appointmentId + "&size=100",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, byVisit.getStatusCode());
    assertTrue(byVisit.getBody().toString().contains("OPENED"));
    assertTrue(byVisit.getBody().toString().contains("opened=true"));
  }

  @Test
  void manualSendIdempotencyRetryAndFilters() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    HttpHeaders createHeaders = bearer(customerToken);
    createHeaders.add("Idempotency-Key", "key-" + UUID.randomUUID());
    UUID appointmentId =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    createHeaders),
                AppointmentDtos.AppointmentResponse.class)
            .getBody()
            .id();

    ResponseEntity<String> missingKey =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/notifications",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"subject":"Visit us again","body":"Thanks for coming in."}
                """,
                bearer(staffToken)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, missingKey.getStatusCode());
    assertTrue(missingKey.getBody().contains("MISSING_HEADER"));

    HttpHeaders sendHeaders = bearer(staffToken);
    sendHeaders.add("Idempotency-Key", "manual-" + appointmentId);
    String mailBody =
        """
        {"subject":"Visit us again","body":"Thanks for coming in."}
        """;
    ResponseEntity<Map> first =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/notifications",
            HttpMethod.POST,
            new HttpEntity<>(mailBody, sendHeaders),
            Map.class);
    assertEquals(HttpStatus.ACCEPTED, first.getStatusCode());
    UUID notificationId = UUID.fromString(first.getBody().get("id").toString());
    ResponseEntity<Map> replay =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/notifications",
            HttpMethod.POST,
            new HttpEntity<>(mailBody, sendHeaders),
            Map.class);
    assertEquals(HttpStatus.ACCEPTED, replay.getStatusCode());
    assertEquals(notificationId.toString(), replay.getBody().get("id").toString());
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE appointment_id = ? AND generation = 'MANUAL'",
            Integer.class,
            appointmentId));

    publisher.drain();
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline
        && stub.recorded().stream().noneMatch(s -> appointmentId.equals(s.appointmentId()))) {
      publisher.drain();
      Thread.sleep(50);
    }
    assertTrue(stub.recorded().stream().anyMatch(s -> appointmentId.equals(s.appointmentId())));

    jdbc.update(
        """
        UPDATE notifications
        SET status = 'RETRY_SCHEDULED'::notification_status,
            attempts = 1,
            next_attempt_at = now() - interval '1 second',
            sent_at = NULL,
            locked_by = NULL,
            lease_expires_at = NULL,
            updated_at = now()
        WHERE id = ?
        """,
        notificationId);
    stub.clear();
    notifications.pollManualRetries("test-manual-poller");
    deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline
        && stub.recorded().stream().noneMatch(s -> appointmentId.equals(s.appointmentId()))) {
      publisher.drain();
      Thread.sleep(50);
    }
    assertTrue(stub.recorded().stream().anyMatch(s -> appointmentId.equals(s.appointmentId())));
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            "SELECT attempts FROM notifications WHERE id = ?", Integer.class, notificationId));

    ResponseEntity<String> notScheduled =
        http.exchange(
            "/api/v1/notifications?status=NOT_SCHEDULED",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, notScheduled.getStatusCode());

    Instant same = Instant.parse("2026-09-24T00:00:00Z");
    ResponseEntity<String> emptyWindow =
        http.exchange(
            "/api/v1/notifications/stats?from=" + same + "&to=" + same,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, emptyWindow.getStatusCode());

    HttpHeaders hook = new HttpHeaders();
    hook.set(HttpHeaders.AUTHORIZATION, "Bearer test-webhook-secret");
    hook.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    ResponseEntity<Void> unknown =
        http.exchange(
            "/api/v1/webhooks/delivery/stub",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"event":"OPENED","correlationKey":"%s","providerEventId":"missing"}
                """
                    .formatted(UUID.randomUUID()),
                hook),
            Void.class);
    assertEquals(HttpStatus.NO_CONTENT, unknown.getStatusCode());

    http.exchange(
        "/api/v1/appointments/" + appointmentId + "/cancel",
        HttpMethod.POST,
        new HttpEntity<>(bearer(staffToken)),
        String.class);
    HttpHeaders afterCancel = bearer(staffToken);
    afterCancel.add("Idempotency-Key", "manual-cancelled-" + appointmentId);
    ResponseEntity<String> cancelled =
        http.exchange(
            "/api/v1/appointments/" + appointmentId + "/notifications",
            HttpMethod.POST,
            new HttpEntity<>(mailBody, afterCancel),
            String.class);
    assertEquals(HttpStatus.ACCEPTED, cancelled.getStatusCode());
  }

  @Test
  void expiredIdempotencyKeysArePurged() {
    UUID userId = me(registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER)).id();
    UUID expiredId = UUID.randomUUID();
    UUID liveId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO idempotency_keys
          (id, user_id, key, fingerprint, status, expires_at, created_at, updated_at)
        VALUES
          (?, ?, ?, 'expired-fingerprint', 'COMPLETED'::idempotency_status,
           now() - interval '1 hour', now(), now()),
          (?, ?, ?, 'live-fingerprint', 'COMPLETED'::idempotency_status,
           now() + interval '1 hour', now(), now())
        """,
        expiredId,
        userId,
        "expired-" + expiredId,
        liveId,
        userId,
        "live-" + liveId);

    idempotency.purgeExpired();

    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM idempotency_keys WHERE id = ?", Integer.class, expiredId));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM idempotency_keys WHERE id = ?", Integer.class, liveId));
  }

  @Test
  void listEndpointsBatchNestedRefs() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(4)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    int rows = 8;
    for (int i = 0; i < rows; i++) {
      UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
      HttpHeaders headers = bearer(customerToken);
      headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
      ResponseEntity<AppointmentDtos.AppointmentResponse> created =
          http.exchange(
              "/api/v1/appointments",
              HttpMethod.POST,
              new HttpEntity<>(
                  """
                  {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                  """
                      .formatted(vehicleId, dealershipId, when.plusHours(i)),
                  headers),
              AppointmentDtos.AppointmentResponse.class);
      assertEquals(HttpStatus.CREATED, created.getStatusCode());
    }

    Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    long started = System.nanoTime();
    ResponseEntity<Map> listed =
        http.exchange(
            "/api/v1/appointments?size=100",
            HttpMethod.GET,
            new HttpEntity<>(bearer(customerToken)),
            Map.class);
    long appointmentListMs = (System.nanoTime() - started) / 1_000_000;
    assertEquals(HttpStatus.OK, listed.getStatusCode());
    assertTrue(listed.getBody().toString().contains("Honda"));
    long appointmentStatements = stats.getPrepareStatementCount();
    assertTrue(
        appointmentStatements >= 3 && appointmentStatements <= 16,
        "appointment list statements="
            + appointmentStatements
            + " (N+1 would grow with page size)");
    assertTrue(appointmentListMs < 2000, "appointment list took " + appointmentListMs + "ms");

    stats.clear();
    started = System.nanoTime();
    ResponseEntity<Map> customers =
        http.exchange(
            "/api/v1/customers?size=100",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    long customerListMs = (System.nanoTime() - started) / 1_000_000;
    assertEquals(HttpStatus.OK, customers.getStatusCode());
    long customerStatements = stats.getPrepareStatementCount();
    assertTrue(
        customerStatements >= 2 && customerStatements <= 16,
        "customer list statements=" + customerStatements + " (N+1 would grow with page size)");
    assertTrue(customerListMs < 2000, "customer list took " + customerListMs + "ms");
  }
}
