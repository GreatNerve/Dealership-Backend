package com.dealership.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.identity.AuthDtos;
import com.dealership.identity.Role;
import com.dealership.notification.smtp.StubNotificationSender;
import com.dealership.reminder.ReminderScheduler;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired IdempotencyService idempotency;

  @Autowired EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void clearStub() {
    stub.clear();
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
  void concurrentClaimsSendOnce() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("TN"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(3)
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
        "UPDATE reminders SET scheduled_at = now() - interval '1 minute' WHERE appointment_id = ?",
        appointmentId);

    Thread t1 = new Thread(poller::tick);
    Thread t2 = new Thread(poller::tick);
    t1.start();
    t2.start();
    t1.join();
    t2.join();
    Thread.sleep(2000);

    Integer outbox =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id IN (SELECT id FROM reminders"
                + " WHERE appointment_id = ?)",
            Integer.class,
            appointmentId);
    assertTrue(outbox >= 1);
    long stubSends =
        stub.recorded().stream().filter(s -> s.appointmentId().equals(appointmentId)).count();
    assertTrue(stubSends <= 2);
    Integer notifications =
        jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE appointment_id = ?",
            Integer.class,
            appointmentId);
    assertTrue(notifications <= 2);
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
                Map.of("email", walkInEmail, "password", "password1"), bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.CREATED, createdCustomer.getStatusCode());
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
  void expiredIdempotencyKeysArePurged() {
    UUID expiredId = UUID.randomUUID();
    UUID liveId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO idempotency_keys
          (id, key, fingerprint, status, expires_at, created_at, updated_at)
        VALUES
          (?, ?, 'expired-fingerprint', 'COMPLETED'::idempotency_status,
           now() - interval '1 hour', now(), now()),
          (?, ?, 'live-fingerprint', 'COMPLETED'::idempotency_status,
           now() + interval '1 hour', now(), now())
        """,
        expiredId,
        "expired-" + expiredId,
        liveId,
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
