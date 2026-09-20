package com.dealership.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dealership.AbstractIT;
import com.dealership.identity.Role;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.appointments.one-confirmed-per-vehicle=false")
class OneConfirmedPerVehicleOffIT extends AbstractIT {

  @Test
  void sameVehicleMayHaveTwoConfirmedWhenCapOff() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(3)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
    AppointmentDtos.AppointmentResponse first = book(customerToken, vehicleId, dealershipId, when);
    AppointmentDtos.AppointmentResponse second =
        book(customerToken, vehicleId, dealershipId, when.plusDays(1));
    assertNotEquals(first.id(), second.id());
  }

  private AppointmentDtos.AppointmentResponse book(
      String token, UUID vehicleId, UUID dealershipId, OffsetDateTime when) {
    HttpHeaders headers = bearer(token);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    String body =
        """
        {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
        """
            .formatted(vehicleId, dealershipId, when);
    ResponseEntity<AppointmentDtos.AppointmentResponse> created =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    return created.getBody();
  }
}
