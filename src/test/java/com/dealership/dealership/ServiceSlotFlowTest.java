package com.dealership.dealership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.appointment.AppointmentDtos;
import com.dealership.identity.Role;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ServiceSlotFlowTest extends AbstractIT {

  @Test
  void customerRejectsUnalignedInstant() {
    Shop shop = openShop();
    OffsetDateTime when = futureVisit().withMinute(7);
    ResponseEntity<ApiResponse<Object>> res =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                """
                    .formatted(shop.vehicleId, shop.dealershipId, when),
                withKey(shop.customerToken)),
            new ParameterizedTypeReference<>() {});
    assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    assertEquals(ApiErrorCode.NOT_A_SERVICE_SLOT, res.getBody().error());
  }

  @Test
  void customerTooFarAhead() {
    Shop shop = openShop();
    OffsetDateTime when =
        alignSlot(
            futureVisit()
                .plusDays(20)
                .atZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .toOffsetDateTime());
    ResponseEntity<ApiResponse<Object>> res =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                """
                    .formatted(shop.vehicleId, shop.dealershipId, when),
                withKey(shop.customerToken)),
            new ParameterizedTypeReference<>() {});
    assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    assertEquals(ApiErrorCode.TOO_FAR_AHEAD, res.getBody().error());
  }

  @Test
  void concurrentCustomersOneLastSeat() throws Exception {
    Shop shop = openShop();
    setCapacity(shop, 1);
    UUID vehicle2 = createVehicle(shop.customerToken, randomPlate("MH"));
    OffsetDateTime when = futureVisit();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<HttpStatus> a =
          pool.submit(
              () -> bookStatus(shop.customerToken, shop.vehicleId, shop.dealershipId, when, start));
      Future<HttpStatus> b =
          pool.submit(
              () -> bookStatus(shop.customerToken, vehicle2, shop.dealershipId, when, start));
      start.countDown();
      HttpStatus first = a.get(20, TimeUnit.SECONDS);
      HttpStatus second = b.get(20, TimeUnit.SECONDS);
      List<HttpStatus> codes = new ArrayList<>(List.of(first, second));
      assertTrue(codes.contains(HttpStatus.CREATED));
      assertTrue(codes.contains(HttpStatus.CONFLICT));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void staffMayOverbookFullSlot() {
    Shop shop = openShop();
    setCapacity(shop, 1);
    OffsetDateTime when = futureVisit();
    assertEquals(
        HttpStatus.CREATED,
        bookStatus(shop.customerToken, shop.vehicleId, shop.dealershipId, when, null));
    UUID staffVehicle = createVehicle(shop.customerToken, randomPlate("TN"));
    ResponseEntity<AppointmentDtos.AppointmentResponse> staffBook =
        http.exchange(
            "/api/v1/appointments",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"customerId":"%s","vehicleId":"%s","scheduledAt":"%s","notify":false}
                """
                    .formatted(shop.customerId, staffVehicle, when),
                withKey(shop.staffToken)),
            AppointmentDtos.AppointmentResponse.class);
    assertEquals(HttpStatus.CREATED, staffBook.getStatusCode());
  }

  @Test
  void overlappingOverrideRejected() {
    Shop shop = openShop();
    LocalDate day = futureVisit().toLocalDate();
    ResponseEntity<DealershipDtos.OverrideResponse> created =
        http.exchange(
            "/api/v1/dealerships/" + shop.dealershipId + "/overrides",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("fromDate", day.toString(), "toDate", day.toString(), "capacity", 0),
                bearer(shop.staffToken)),
            DealershipDtos.OverrideResponse.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    ResponseEntity<ApiResponse<Object>> overlap =
        http.exchange(
            "/api/v1/dealerships/" + shop.dealershipId + "/overrides",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("fromDate", day.toString(), "toDate", day.toString(), "capacity", 2),
                bearer(shop.staffToken)),
            new ParameterizedTypeReference<>() {});
    assertEquals(HttpStatus.CONFLICT, overlap.getStatusCode());
    assertEquals(ApiErrorCode.OVERRIDE_OVERLAPS, overlap.getBody().error());
  }

  @Test
  void hoursChangeBlockedByConfirmed() {
    Shop shop = openShop();
    OffsetDateTime when = futureVisit();
    assertEquals(
        HttpStatus.CREATED,
        bookStatus(shop.customerToken, shop.vehicleId, shop.dealershipId, when, null));
    List<Map<String, Object>> closed = new ArrayList<>();
    for (int d = 1; d <= 7; d++) {
      closed.add(Map.of("weekday", d, "closed", true));
    }
    ResponseEntity<ApiResponse<Object>> res =
        http.exchange(
            "/api/v1/dealerships/" + shop.dealershipId + "/schedule",
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("defaultCapacity", 10, "hours", closed), bearer(shop.staffToken)),
            new ParameterizedTypeReference<>() {});
    assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
    assertEquals(ApiErrorCode.SCHEDULE_CONFLICT, res.getBody().error());
  }

  private Shop openShop() {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    UUID vehicleId = createVehicle(customerToken, randomPlate("KA"));
    UUID customerId = me(customerToken).customerId();
    return new Shop(staffToken, customerToken, dealershipId, customerId, vehicleId);
  }

  private void setCapacity(Shop shop, int capacity) {
    DealershipDtos.ScheduleResponse schedule =
        http.exchange(
                "/api/v1/dealerships/" + shop.dealershipId + "/schedule",
                HttpMethod.GET,
                new HttpEntity<>(bearer(shop.staffToken)),
                DealershipDtos.ScheduleResponse.class)
            .getBody();
    http.exchange(
        "/api/v1/dealerships/" + shop.dealershipId + "/schedule",
        HttpMethod.PUT,
        new HttpEntity<>(
            Map.of("defaultCapacity", capacity, "hours", schedule.hours()),
            bearer(shop.staffToken)),
        DealershipDtos.ScheduleResponse.class);
  }

  private HttpStatus bookStatus(
      String token, UUID vehicleId, UUID dealershipId, OffsetDateTime when, CountDownLatch start) {
    if (start != null) {
      try {
        start.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return HttpStatus.INTERNAL_SERVER_ERROR;
      }
    }
    int code =
        http.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s","notify":false}
                    """
                        .formatted(vehicleId, dealershipId, when),
                    withKey(token)),
                String.class)
            .getStatusCode()
            .value();
    HttpStatus status = HttpStatus.resolve(code);
    return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private org.springframework.http.HttpHeaders withKey(String token) {
    var headers = bearer(token);
    headers.add("Idempotency-Key", "key-" + UUID.randomUUID());
    return headers;
  }

  private record Shop(
      String staffToken,
      String customerToken,
      UUID dealershipId,
      UUID customerId,
      UUID vehicleId) {}
}
