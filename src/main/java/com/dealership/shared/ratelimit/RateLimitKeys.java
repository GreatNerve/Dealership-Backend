package com.dealership.shared.ratelimit;

import java.util.Locale;
import java.util.regex.Pattern;

final class RateLimitKeys {

  // UUID path segments must not split the bucket (GET /appointments/{id} is one endpoint).
  private static final Pattern UUID_SEGMENT =
      Pattern.compile(
          "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private RateLimitKeys() {}

  static String endpoint(String method, String requestUri) {
    String path = requestUri == null ? "" : requestUri;
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    path = UUID_SEGMENT.matcher(path).replaceAll("/{id}");
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
    return verb + ":" + path;
  }

  static boolean loginOrRegister(String endpoint) {
    return "POST:/api/v1/auth/login".equals(endpoint)
        || "POST:/api/v1/auth/register".equals(endpoint);
  }
}
