package com.dealership.shared.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dealership.identity.Role;
import com.dealership.shared.config.AppProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void shortSecretIsRejected() {
    AppProperties properties = new AppProperties();
    properties.getJwt().setSecret("too-short");
    JwtService jwt = new JwtService(properties);
    assertThrows(
        IllegalStateException.class, () -> jwt.issue(UUID.randomUUID(), "a@b.com", Role.CUSTOMER));
  }
}
