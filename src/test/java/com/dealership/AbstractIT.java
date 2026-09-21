package com.dealership;

import com.dealership.dealership.DealershipDtos;
import com.dealership.identity.AuthDtos;
import com.dealership.identity.Role;
import com.dealership.vehicle.VehicleDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIT {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  static {
    POSTGRES.start();
    RABBIT.start();
    REDIS.start();
  }

  @Autowired protected TestRestTemplate http;

  @Autowired
  void unwrapApiEnvelope(ObjectMapper mapper) {
    http.getRestTemplate()
        .getMessageConverters()
        .replaceAll(
            converter ->
                converter instanceof MappingJackson2HttpMessageConverter
                    ? new EnvelopeUnwrappingConverter(mapper)
                    : converter);
  }

  @DynamicPropertySource
  static void containers(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  protected String registerAndLogin(String email, Role role) {
    http.postForEntity(
        "/api/v1/auth/register",
        Map.of("email", email, "password", "password1", "role", role.name()),
        AuthDtos.UserResponse.class);
    AuthDtos.TokenResponse token =
        http.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "password1"),
                AuthDtos.TokenResponse.class)
            .getBody();
    return token.accessToken();
  }

  protected AuthDtos.UserResponse me(String token) {
    return http.exchange(
            "/api/v1/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            AuthDtos.UserResponse.class)
        .getBody();
  }

  protected UUID createDealership(String token) {
    return createDealership(token, "Asia/Kolkata");
  }

  protected UUID createDealership(String token, String timezone) {
    ResponseEntity<DealershipDtos.DealershipResponse> res =
        http.exchange(
            "/api/v1/dealerships",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "name", "Shop " + UUID.randomUUID(), "timezone", timezone, "address", "1 Road"),
                bearer(token)),
            DealershipDtos.DealershipResponse.class);
    if (res.getStatusCode() != HttpStatus.CREATED || res.getBody() == null) {
      throw new IllegalStateException("create dealership failed: " + res.getStatusCode());
    }
    return res.getBody().id();
  }

  protected UUID createVehicle(String token, String registrationNumber) {
    ResponseEntity<VehicleDtos.VehicleResponse> res =
        http.exchange(
            "/api/v1/vehicles",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "registrationNumber",
                    registrationNumber,
                    "make",
                    "Honda",
                    "model",
                    "Civic",
                    "year",
                    2022),
                bearer(token)),
            VehicleDtos.VehicleResponse.class);
    if (res.getStatusCode() != HttpStatus.CREATED || res.getBody() == null) {
      throw new IllegalStateException("create vehicle failed: " + res.getStatusCode());
    }
    return res.getBody().id();
  }

  protected static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("Content-Type", "application/json");
    return headers;
  }

  protected static String randomPlate(String state) {
    String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
    return (state + hex).substring(0, 12);
  }
}
