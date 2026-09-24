package com.dealership.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Secrets {

  private Secrets() {}

  public static boolean equal(String expected, String given) {
    if (expected == null || given == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
  }

  /** Bearer, Token, or the raw secret (Brevo Token auth). */
  public static String presented(String authorization) {
    if (authorization == null) {
      return null;
    }
    String given = authorization.trim();
    if (given.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return given.substring(7).trim();
    }
    if (given.regionMatches(true, 0, "Token ", 0, 6)) {
      return given.substring(6).trim();
    }
    return given;
  }
}
