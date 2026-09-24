package com.dealership.shared.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class Inputs {

  private Inputs() {}

  public static String sanitize(String raw) {
    if (raw == null) {
      return null;
    }
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); ) {
      int cp = raw.codePointAt(i);
      if (!isStripped(cp)) {
        out.appendCodePoint(cp);
      }
      i += Character.charCount(cp);
    }
    return out.toString().strip();
  }

  public static String email(String raw) {
    String cleaned = sanitize(raw);
    return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
  }

  public static String clip(String raw, int max) {
    if (raw == null) {
      return null;
    }
    return raw.length() <= max ? raw : raw.substring(0, max);
  }

  // varchar unique; SHA-256 hex is 64 chars so a long provider id still fits
  public static String fit(String raw, int max) {
    if (raw == null) {
      return "";
    }
    if (raw.length() <= max) {
      return raw;
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean isStripped(int cp) {
    int type = Character.getType(cp);
    return Character.isISOControl(cp)
        || type == Character.FORMAT
        || type == Character.PRIVATE_USE
        || type == Character.SURROGATE;
  }
}
