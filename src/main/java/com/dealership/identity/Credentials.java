package com.dealership.identity;

import org.springframework.security.crypto.password.PasswordEncoder;

final class Credentials {

  private Credentials() {}

  static boolean matches(PasswordEncoder encoder, String raw, String stored, String dummyHash) {
    String hash = stored != null ? stored : dummyHash;
    return encoder.matches(raw == null ? "" : raw, hash);
  }
}
