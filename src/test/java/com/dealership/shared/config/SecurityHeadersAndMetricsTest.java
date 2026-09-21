package com.dealership.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.identity.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class SecurityHeadersAndMetricsTest extends AbstractIT {

  @Test
  void healthSendsNosniffAndFrameDeny() {
    ResponseEntity<String> health = http.getForEntity("/actuator/health", String.class);
    assertEquals(HttpStatus.OK, health.getStatusCode());
    assertEquals("nosniff", health.getHeaders().getFirst("X-Content-Type-Options"));
    assertEquals("DENY", health.getHeaders().getFirst("X-Frame-Options"));
    String csp = health.getHeaders().getFirst("Content-Security-Policy");
    assertNotNull(csp);
    assertTrue(csp.contains("frame-ancestors 'none'"));
  }

  @Test
  void prometheusRequiresJwtAndExposesDealershipMeters() {
    ResponseEntity<String> anonymous = http.getForEntity("/actuator/prometheus", String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
    String token =
        registerAndLogin("ops-" + java.util.UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    HttpHeaders headers = bearer(token);
    headers.setAccept(List.of(MediaType.TEXT_PLAIN));
    headers.remove(HttpHeaders.CONTENT_TYPE);
    ResponseEntity<String> scraped =
        http.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertEquals(HttpStatus.OK, scraped.getStatusCode());
    String body = scraped.getBody();
    assertNotNull(body);
    assertTrue(
        body.contains("dealership_appointments_total"),
        () -> "missing dealership meters: " + body.substring(0, Math.min(body.length(), 800)));
    assertTrue(
        body.contains("http_server_requests") || body.contains("jvm_memory"),
        () -> "missing http/jvm meters: " + body.substring(0, Math.min(body.length(), 800)));
  }
}
