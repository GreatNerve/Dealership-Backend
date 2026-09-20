package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.identity.AuthDtos;
import com.dealership.identity.Role;
import com.dealership.vehicle.VehicleDtos;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class InputValidationTest extends AbstractIT {

  @Test
  void registerRejectsInvalidEmailAndNormalizesDirtyEmail() {
    ResponseEntity<String> bad =
        http.postForEntity(
            "/api/v1/auth/register",
            Map.of("email", "not-an-email", "password", "password1", "role", Role.CUSTOMER.name()),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
    assertTrue(bad.getBody().contains("VALIDATION_ERROR"));

    String local = "val-" + UUID.randomUUID();
    ResponseEntity<AuthDtos.UserResponse> created =
        http.postForEntity(
            "/api/v1/auth/register",
            Map.of(
                "email",
                "  " + local + "@Ex.COM\u0000  ",
                "password",
                "password1",
                "role",
                Role.CUSTOMER.name()),
            AuthDtos.UserResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertEquals(local + "@ex.com", created.getBody().email());
  }

  @Test
  void formLoginRejectsShortPassword() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "swagger");
    form.add("username", "customer@demo.local");
    form.add("password", "short");
    ResponseEntity<String> oauth =
        http.postForEntity("/api/v1/auth/login", new HttpEntity<>(form, headers), String.class);
    assertEquals(HttpStatus.BAD_REQUEST, oauth.getStatusCode());
    assertTrue(oauth.getBody().contains("VALIDATION_ERROR"));
  }

  @Test
  void vehicleNormalizesDirtyRegistrationAndRejectsBlankMake() {
    String token = registerAndLogin("veh-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    String plate = randomPlate("KA");
    String dirty =
        " \u0000"
            + plate.substring(0, 2).toLowerCase()
            + "-"
            + plate.substring(2, 4)
            + "-"
            + plate.substring(4, 6).toLowerCase()
            + "-"
            + plate.substring(6)
            + " ";
    ResponseEntity<VehicleDtos.VehicleResponse> created =
        http.exchange(
            "/api/v1/vehicles",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "registrationNumber", dirty, "make", " Honda ", "model", "Civic", "year", 2022),
                bearer(token)),
            VehicleDtos.VehicleResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertEquals(plate, created.getBody().registrationNumber());
    assertEquals("Honda", created.getBody().make());

    ResponseEntity<String> blankMake =
        http.exchange(
            "/api/v1/vehicles",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "registrationNumber",
                    randomPlate("MH"),
                    "make",
                    "   ",
                    "model",
                    "Civic",
                    "year",
                    2022),
                bearer(token)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, blankMake.getStatusCode());
    assertTrue(blankMake.getBody().contains("VALIDATION_ERROR"));
  }

  @Test
  void appointmentRejectsBlankScheduledAt() {
    String staffToken =
        registerAndLogin("staff-val-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken =
        registerAndLogin("cust-val-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("DL"));
    HttpHeaders headers = bearer(customerToken);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    ResponseEntity<String> res =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"  ","notify":true}
                """
                    .formatted(vehicleId, dealershipId),
                headers),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    assertTrue(res.getBody().contains("VALIDATION_ERROR"));
  }

  @Test
  void dealershipRejectsInvalidTimezoneAndListRejectsLongQ() {
    String token =
        registerAndLogin("shop-val-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    ResponseEntity<String> tz =
        http.exchange(
            "/api/v1/dealerships",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("name", "Shop", "timezone", "Not/AZone", "address", "1 Road"),
                bearer(token)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, tz.getStatusCode());
    assertTrue(tz.getBody().contains("INVALID_TIMEZONE"));

    ResponseEntity<String> q =
        http.exchange(
            "/api/v1/dealerships?q=" + "x".repeat(101),
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            String.class);
    assertEquals(HttpStatus.BAD_REQUEST, q.getStatusCode());
    assertTrue(q.getBody().contains("INVALID_Q"));
  }

  @Test
  void authErrorsUseTheSameEnvelope() {
    ResponseEntity<String> unauth = http.getForEntity("/api/v1/me", String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, unauth.getStatusCode());
    assertTrue(unauth.getBody().contains("\"success\":false"));
    assertTrue(unauth.getBody().contains("UNAUTHORIZED"));
    assertTrue(unauth.getBody().contains("correlationId"));
    assertTrue(unauth.getBody().contains("\"data\":null"));

    String customer = registerAndLogin("env-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    ResponseEntity<String> forbidden =
        http.exchange(
            "/api/v1/customers", HttpMethod.GET, new HttpEntity<>(bearer(customer)), String.class);
    assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    assertTrue(forbidden.getBody().contains("\"success\":false"));
    assertTrue(forbidden.getBody().contains("FORBIDDEN"));
    assertTrue(forbidden.getBody().contains("correlationId"));
  }
}
