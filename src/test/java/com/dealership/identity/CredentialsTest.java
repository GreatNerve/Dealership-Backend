package com.dealership.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class CredentialsTest {

  @Test
  void missingUserStillRunsBcryptAgainstDummyHash() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    String dummy = encoder.encode("login-miss");
    String real = encoder.encode("password1");
    assertTrue(Credentials.matches(encoder, "password1", real, dummy));
    assertFalse(Credentials.matches(encoder, "password1", null, dummy));
    assertFalse(Credentials.matches(encoder, "wrong", real, dummy));
    assertFalse(Credentials.matches(encoder, null, null, dummy));
  }
}
