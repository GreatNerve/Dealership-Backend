package com.dealership.shared.api;

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

  private static boolean isStripped(int cp) {
    int type = Character.getType(cp);
    return Character.isISOControl(cp)
        || type == Character.FORMAT
        || type == Character.PRIVATE_USE
        || type == Character.SURROGATE;
  }
}
