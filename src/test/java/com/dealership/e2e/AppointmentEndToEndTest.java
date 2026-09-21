package com.dealership.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.appointment.AppointmentDtos;
import com.dealership.appointment.AppointmentStatus;
import com.dealership.identity.Role;
import com.dealership.notification.FileNotificationLog;
import com.dealership.notification.OutboxPublisher;
import com.dealership.notification.smtp.StubNotificationSender;
import com.dealership.reminder.ReminderScheduler;
import com.dealership.shared.api.PageResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("e2e")
class AppointmentEndToEndTest extends AbstractIT {

  private static final ParameterizedTypeReference<PageResponse<AppointmentDtos.AppointmentResponse>>
      APPOINTMENT_PAGE = new ParameterizedTypeReference<>() {};

  @Autowired JdbcTemplate jdbc;
  @Autowired StubNotificationSender stub;
  @Autowired FileNotificationLog notifyOffLog;
  @Autowired ReminderScheduler poller;
  @Autowired OutboxPublisher publisher;

  @BeforeEach
  void clearStub() {
    stub.clear();
    notifyOffLog.clear();
  }

  @Test
  void dueReminderSendsOnceWithBookingOffsetWallTime() {
    Shop shop = open("America/New_York");
    AppointmentDtos.AppointmentResponse created =
        createCustomerAppointment(shop, future(5, 30), true);
    assertEquals("+05:30", created.displayOffset());
    assertTrue(created.scheduledAtLocal().contains("+05:30"));

    ResponseEntity<AppointmentDtos.AppointmentResponse> staffView =
        http.exchange(
            "/api/v1/appointments/" + created.id(),
            HttpMethod.GET,
            new HttpEntity<>(bearer(shop.staffToken())),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.OK, staffView.getStatusCode());
    assertFalse(staffView.getBody().scheduledAtLocal().contains("+05:30"));

    jdbc.update(
        """
        UPDATE reminders SET scheduled_at = now() - interval '1 minute'
        WHERE appointment_id = ? AND offset_minutes = 1440
        """,
        created.id());
    poller.tick();
    publisher.drain();
    assertTrue(waitForStub(created.id(), 1));
    long sends =
        stub.recorded().stream().filter(s -> s.appointmentId().equals(created.id())).count();
    assertEquals(1, sends);
    assertTrue(
        stub.recorded().stream()
            .anyMatch(
                s -> s.appointmentId().equals(created.id()) && s.wallTime().contains("UTC+05:30")));
    assertEquals(
        Integer.valueOf(1),
        jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE appointment_id = ? AND status = 'SENT'",
            Integer.class,
            created.id()));
  }

  @Test
  void staffBooksHomeDealershipWithoutSendingDealershipId() {
    Shop shop = open("Asia/Kolkata");
    OffsetDateTime when = future(5, 30);
    HttpHeaders headers = bearer(shop.staffToken());
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    String body =
        """
        {"customerId":"%s","vehicleId":"%s","scheduledAt":"%s"}
        """
            .formatted(shop.customerId(), shop.vehicleId(), when);
    ResponseEntity<AppointmentDtos.AppointmentResponse> created =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertEquals(shop.dealershipId(), created.getBody().dealershipId());
    assertEquals(Role.DEALERSHIP_STAFF, created.getBody().createdByRole());
  }

  @Test
  void staffSendingDealershipIdIsRejected() {
    Shop shop = open("Asia/Kolkata");
    HttpHeaders headers = bearer(shop.staffToken());
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    String body =
        """
        {"customerId":"%s","vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
        """
            .formatted(shop.customerId(), shop.vehicleId(), shop.dealershipId(), future(5, 30));
    ResponseEntity<String> res =
        http.exchange(
            "/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    assertTrue(res.getBody().contains("STAFF_DEALERSHIP_FROM_HOME"));
  }

  @Test
  void cancelBeforeDueDoesNotSend() {
    Shop shop = open("Asia/Kolkata");
    var created = createCustomerAppointment(shop, future(5, 30), true);
    ResponseEntity<AppointmentDtos.AppointmentResponse> cancelled =
        http.exchange(
            "/api/v1/appointments/" + created.id() + "/cancel",
            HttpMethod.POST,
            new HttpEntity<>(bearer(shop.customerToken())),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(AppointmentStatus.CANCELLED, cancelled.getBody().status());
    jdbc.update(
        "UPDATE reminders SET scheduled_at = now() - interval '1 minute' WHERE appointment_id = ?",
        created.id());
    poller.tick();
    publisher.drain();
    assertFalse(waitForStub(created.id(), 1, 500));
  }

  @Test
  void rescheduleCancelsOldRemindersAndOpensNewOffsets() {
    Shop shop = open("Asia/Kolkata");
    var created = createCustomerAppointment(shop, future(5, 30), true);
    HttpHeaders headers = bearer(shop.customerToken());
    ResponseEntity<AppointmentDtos.AppointmentResponse> moved =
        http.exchange(
            "/api/v1/appointments/" + created.id() + "/reschedule",
            HttpMethod.POST,
            new HttpEntity<>("{\"scheduledAt\":\"" + future(5, 30).plusDays(2) + "\"}", headers),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            "SELECT MAX(schedule_version) FROM reminders WHERE appointment_id = ?",
            Integer.class,
            created.id()));
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            "SELECT count(*) FROM reminders WHERE appointment_id = ? AND status = 'CANCELLED'",
            Integer.class,
            created.id()));
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            """
            SELECT count(*) FROM reminders
            WHERE appointment_id = ? AND schedule_version = 2 AND status = 'PENDING'
            """,
            Integer.class,
            created.id()));
  }

  @Test
  void notifyOffAppendsLogAndStoresNotification() {
    Shop shop = open("Asia/Kolkata");
    var created = createCustomerAppointment(shop, future(5, 30), false);
    assertFalse(created.notifyEnabled());
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            "SELECT count(*) FROM reminders WHERE appointment_id = ?",
            Integer.class,
            created.id()));
    jdbc.update(
        "UPDATE reminders SET scheduled_at = now() - interval '1 minute' WHERE appointment_id = ?",
        created.id());
    poller.tick();
    publisher.drain();
    poller.tick();
    publisher.drain();
    assertTrue(waitForNotifyOff(created.id(), 2));
    assertFalse(waitForStub(created.id(), 1, 500));
    assertEquals(
        Integer.valueOf(2),
        jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE appointment_id = ? AND status = 'SENT'",
            Integer.class,
            created.id()));
  }

  @Test
  void customerCannotReadAnotherCustomersAppointment() {
    Shop shop = open("Asia/Kolkata");
    var created = createCustomerAppointment(shop, future(5, 30), true);
    String other = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    ResponseEntity<String> hidden =
        http.exchange(
            "/api/v1/appointments/" + created.id(),
            HttpMethod.GET,
            new HttpEntity<>(bearer(other)),
            String.class);
    assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
  }

  @Test
  void staffCannotReadAnotherDealershipAppointment() {
    Shop shop = open("Asia/Kolkata");
    var created = createCustomerAppointment(shop, future(5, 30), true);
    String otherStaff =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    createDealership(otherStaff);
    ResponseEntity<String> hidden =
        http.exchange(
            "/api/v1/appointments/" + created.id(),
            HttpMethod.GET,
            new HttpEntity<>(bearer(otherStaff)),
            String.class);
    assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
  }

  @Test
  void listsArePaginatedAndOversizedPageIsRejected() {
    Shop shop = open("Asia/Kolkata");
    createCustomerAppointment(shop, future(5, 30), true);
    ResponseEntity<PageResponse<AppointmentDtos.AppointmentResponse>> page =
        http.exchange(
            "/api/v1/appointments?page=0&size=20",
            HttpMethod.GET,
            new HttpEntity<>(bearer(shop.customerToken())),
            APPOINTMENT_PAGE);
    assertEquals(HttpStatus.OK, page.getStatusCode());
    assertEquals(0, page.getBody().page());
    assertEquals(20, page.getBody().size());
    assertTrue(page.getBody().totalElements() >= 1);
    assertFalse(page.getBody().items().isEmpty());

    ResponseEntity<String> tooBig =
        http.exchange(
            "/api/v1/appointments?size=1001",
            HttpMethod.GET,
            new HttpEntity<>(bearer(shop.customerToken())),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, tooBig.getStatusCode());
    assertTrue(tooBig.getBody().contains("INVALID_SIZE"));
  }

  private AppointmentDtos.AppointmentResponse createCustomerAppointment(
      Shop shop, OffsetDateTime when, boolean notify) {
    HttpHeaders headers = bearer(shop.customerToken());
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    String body =
        """
        {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":%s}
        """
            .formatted(shop.vehicleId(), shop.dealershipId(), when, notify);
    ResponseEntity<AppointmentDtos.AppointmentResponse> created =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    return created.getBody();
  }

  private Shop open(String timezone) {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken, timezone);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    UUID customerId = me(customerToken).customerId();
    return new Shop(staffToken, customerToken, dealershipId, customerId, vehicleId);
  }

  private static OffsetDateTime future(int hours, int minutes) {
    return OffsetDateTime.now(ZoneOffset.UTC)
        .plusDays(3)
        .withOffsetSameInstant(ZoneOffset.ofHoursMinutes(hours, minutes));
  }

  private boolean waitForStub(UUID appointmentId, int min) {
    return waitForStub(appointmentId, min, 5000);
  }

  private boolean waitForStub(UUID appointmentId, int min, long timeoutMs) {
    return waitForCount(
        () -> stub.recorded().stream().filter(s -> s.appointmentId().equals(appointmentId)).count(),
        min,
        timeoutMs);
  }

  private boolean waitForNotifyOff(UUID appointmentId, int min) {
    return waitForCount(
        () ->
            notifyOffLog.recorded().stream()
                .filter(s -> s.appointmentId().equals(appointmentId))
                .count(),
        min,
        5000);
  }

  private boolean waitForCount(LongSupplier count, int min, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (count.getAsLong() >= min) {
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

  private record Shop(
      String staffToken,
      String customerToken,
      UUID dealershipId,
      UUID customerId,
      UUID vehicleId) {}
}
