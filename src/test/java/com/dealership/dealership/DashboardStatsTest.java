package com.dealership.dealership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.identity.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DashboardStatsTest extends AbstractIT {

  @Test
  void staffBucketsCustomerForbiddenAndRangeCap() {
    String staffToken =
        registerAndLogin("staff-dash-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    createDealership(staffToken);
    String customerToken =
        registerAndLogin("cust-dash-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);

    Instant from = Instant.parse("2026-01-01T00:00:00+05:30");
    Instant to = Instant.parse("2026-09-25T00:00:00+05:30");
    String qs = "from=" + from + "&to=" + to;

    ResponseEntity<Map> day =
        http.exchange(
            "/api/v1/dashboard/stats?" + qs + "&bucket=DAY",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, day.getStatusCode());
    @SuppressWarnings("unchecked")
    List<?> dayBuckets =
        (List<?>) ((Map<String, Object>) day.getBody().get("appointments")).get("buckets");
    assertTrue(dayBuckets.size() > 1);
    assertTrue(dayBuckets.size() <= 400);

    ResponseEntity<Map> week =
        http.exchange(
            "/api/v1/dashboard/stats?" + qs + "&bucket=WEEK",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, week.getStatusCode());
    @SuppressWarnings("unchecked")
    List<?> weekBuckets =
        (List<?>) ((Map<String, Object>) week.getBody().get("appointments")).get("buckets");
    assertTrue(weekBuckets.size() >= 1);

    ResponseEntity<Map> month =
        http.exchange(
            "/api/v1/dashboard/stats?" + qs + "&bucket=MONTH",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.OK, month.getStatusCode());
    @SuppressWarnings("unchecked")
    List<?> monthBuckets =
        (List<?>) ((Map<String, Object>) month.getBody().get("appointments")).get("buckets");
    assertTrue(monthBuckets.size() >= 1);

    ResponseEntity<Map> customer =
        http.exchange(
            "/api/v1/dashboard/stats?" + qs + "&bucket=DAY",
            HttpMethod.GET,
            new HttpEntity<>(bearer(customerToken)),
            Map.class);
    assertEquals(HttpStatus.FORBIDDEN, customer.getStatusCode());

    ResponseEntity<Map> tooWide =
        http.exchange(
            "/api/v1/dashboard/stats?from=2000-01-01T00:00:00Z&to=2026-09-24T00:00:00Z&bucket=DAY",
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.BAD_REQUEST, tooWide.getStatusCode());

    ResponseEntity<Map> removed =
        http.exchange(
            "/api/v1/appointments/stats/daily?" + qs,
            HttpMethod.GET,
            new HttpEntity<>(bearer(staffToken)),
            Map.class);
    assertEquals(HttpStatus.NOT_FOUND, removed.getStatusCode());
  }
}
