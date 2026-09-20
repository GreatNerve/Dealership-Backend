package com.dealership.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class AuthFlowTest extends AbstractIT {

  @Test
  void loginJsonAndOAuth2FormReturnAccessToken() {
    String email = "auth-" + UUID.randomUUID() + "@ex.com";
    http.postForEntity(
        "/api/v1/auth/register",
        Map.of("email", email, "password", "password1", "role", Role.CUSTOMER.name()),
        AuthDtos.UserResponse.class);

    ResponseEntity<String> json =
        http.postForEntity(
            "/api/v1/auth/login", Map.of("email", email, "password", "password1"), String.class);
    assertEquals(HttpStatus.OK, json.getStatusCode());
    assertOauthTokenBody(json.getBody());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "swagger");
    form.add("username", email);
    form.add("password", "password1");
    ResponseEntity<String> oauth =
        http.postForEntity("/api/v1/auth/login", new HttpEntity<>(form, headers), String.class);
    assertEquals(HttpStatus.OK, oauth.getStatusCode());
    assertOauthTokenBody(oauth.getBody());
  }

  private static void assertOauthTokenBody(String body) {
    assertTrue(body.contains("\"access_token\""));
    assertTrue(body.contains("\"token_type\""));
    assertTrue(body.contains("\"expires_in\""));
    assertFalse(body.contains("\"accessToken\""));
    assertFalse(body.contains("\"tokenType\""));
    assertFalse(body.contains("\"expiresIn\""));
  }
}
