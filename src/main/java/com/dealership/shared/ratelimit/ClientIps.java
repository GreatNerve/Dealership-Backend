package com.dealership.shared.ratelimit;

import java.util.List;

public final class ClientIps {

  private ClientIps() {}

  public static String resolve(
      boolean trustForwardedFor,
      List<String> trustedProxies,
      String remoteAddr,
      String forwardedFor) {
    String remote = remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr.trim();
    if (!trustForwardedFor || forwardedFor == null || forwardedFor.isBlank()) {
      return remote;
    }
    List<String> cidrs =
        trustedProxies == null || trustedProxies.isEmpty() ? Cidrs.CLOUDFLARE : trustedProxies;
    if (!Cidrs.anyContains(cidrs, remote)) {
      return remote;
    }
    String hop = forwardedFor.split(",")[0].trim();
    return hop.isEmpty() ? remote : hop;
  }
}
