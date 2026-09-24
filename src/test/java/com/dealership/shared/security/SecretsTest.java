package com.dealership.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecretsTest {

  @Test
  void equalRejectsMismatchedAndNull() {
    assertTrue(Secrets.equal("secret", "secret"));
    assertFalse(Secrets.equal("secret", "Secret"));
    assertFalse(Secrets.equal("secret", "secre"));
    assertFalse(Secrets.equal(null, "secret"));
    assertFalse(Secrets.equal("secret", null));
  }

  @Test
  void presentedStripsBearerAndToken() {
    assertEquals("abc", Secrets.presented("Bearer abc"));
    assertEquals("abc", Secrets.presented("bearer abc"));
    assertEquals("abc", Secrets.presented("Token abc"));
    assertEquals("abc", Secrets.presented("abc"));
    assertEquals(null, Secrets.presented(null));
  }
}
