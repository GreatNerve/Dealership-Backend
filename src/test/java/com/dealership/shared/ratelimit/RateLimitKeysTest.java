package com.dealership.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimitKeysTest {

  @Test
  void loginAndRegisterAreDifferentEndpoints() {
    String login = RateLimitKeys.endpoint("POST", "/api/v1/auth/login");
    String register = RateLimitKeys.endpoint("POST", "/api/v1/auth/register");
    assertNotEquals(login, register);
    assertTrue(RateLimitKeys.loginOrRegister(login));
    assertTrue(RateLimitKeys.loginOrRegister(register));
  }

  @Test
  void getMeIsNotTheLoginBucket() {
    String me = RateLimitKeys.endpoint("GET", "/api/v1/me");
    assertFalse(RateLimitKeys.loginOrRegister(me));
    assertEquals("GET:/api/v1/me", me);
  }

  @Test
  void appointmentItemIdsShareOneGetBucket() {
    String first =
        RateLimitKeys.endpoint("GET", "/api/v1/appointments/11111111-1111-4111-8111-111111111111");
    String second =
        RateLimitKeys.endpoint("GET", "/api/v1/appointments/22222222-2222-4222-8222-222222222222");
    assertEquals("GET:/api/v1/appointments/{id}", first);
    assertEquals(first, second);
  }

  @Test
  void listAndCreateAppointmentsAreDifferentEndpoints() {
    String list = RateLimitKeys.endpoint("GET", "/api/v1/appointments");
    String create = RateLimitKeys.endpoint("POST", "/api/v1/appointments");
    assertNotEquals(list, create);
    assertFalse(RateLimitKeys.loginOrRegister(create));
  }
}
