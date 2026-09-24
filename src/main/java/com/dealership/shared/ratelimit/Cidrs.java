package com.dealership.shared.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public final class Cidrs {

  // Empty trusted-proxies + trust-forwarded-for: Cloudflare only, so origin-direct XFF cannot reset
  // login buckets.
  static final List<String> CLOUDFLARE =
      List.of(
          "173.245.48.0/20",
          "103.21.244.0/22",
          "103.22.200.0/22",
          "103.31.4.0/22",
          "141.101.64.0/18",
          "108.162.192.0/18",
          "190.93.240.0/20",
          "188.114.96.0/20",
          "197.234.240.0/22",
          "198.41.128.0/17",
          "162.158.0.0/15",
          "104.16.0.0/13",
          "104.24.0.0/14",
          "172.64.0.0/13",
          "131.0.72.0/22",
          "2400:cb00::/32",
          "2606:4700::/32",
          "2803:f800::/32",
          "2405:b500::/32",
          "2405:8100::/32",
          "2a06:98c0::/32",
          "2c0f:f248::/32");

  private Cidrs() {}

  public static boolean anyContains(List<String> cidrs, String host) {
    if (cidrs == null || cidrs.isEmpty() || host == null || host.isBlank()) {
      return false;
    }
    for (String cidr : cidrs) {
      if (contains(cidr, host)) {
        return true;
      }
    }
    return false;
  }

  static boolean contains(String cidr, String host) {
    if (cidr == null || cidr.isBlank() || host == null || host.isBlank()) {
      return false;
    }
    try {
      int slash = cidr.indexOf('/');
      String network = slash < 0 ? cidr.trim() : cidr.substring(0, slash).trim();
      InetAddress net = InetAddress.getByName(network);
      InetAddress addr = InetAddress.getByName(stripZone(host.trim()));
      byte[] n = net.getAddress();
      byte[] a = addr.getAddress();
      if (n.length != a.length) {
        return false;
      }
      int bits = slash < 0 ? n.length * 8 : Integer.parseInt(cidr.substring(slash + 1).trim());
      if (bits < 0 || bits > n.length * 8) {
        return false;
      }
      int fullBytes = bits / 8;
      for (int i = 0; i < fullBytes; i++) {
        if (n[i] != a[i]) {
          return false;
        }
      }
      int rem = bits % 8;
      if (rem == 0) {
        return true;
      }
      int mask = 0xFF << (8 - rem);
      return (n[fullBytes] & mask) == (a[fullBytes] & mask);
    } catch (UnknownHostException | NumberFormatException ex) {
      return false;
    }
  }

  private static String stripZone(String host) {
    int zone = host.indexOf('%');
    return zone < 0 ? host : host.substring(0, zone);
  }
}
