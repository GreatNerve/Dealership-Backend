package com.dealership.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClientIpsTest {

  @Test
  void ignoresXffUnlessRemoteAddrIsATrustedProxy() {
    assertEquals(
        "203.0.113.9", ClientIps.resolve(true, List.of("172.64.0.0/13"), "203.0.113.9", "1.2.3.4"));
    assertEquals(
        "1.2.3.4",
        ClientIps.resolve(true, List.of("172.64.0.0/13"), "172.64.10.1", "1.2.3.4, 10.0.0.1"));
  }

  @Test
  void emptyCidrsStillTrustCloudflareAndNotOriginDirect() {
    assertEquals("1.2.3.4", ClientIps.resolve(true, List.of(), "172.64.10.1", "1.2.3.4"));
    assertEquals("203.0.113.9", ClientIps.resolve(true, List.of(), "203.0.113.9", "1.2.3.4"));
  }

  @Test
  void flagOffIgnoresXff() {
    assertEquals(
        "172.64.10.1",
        ClientIps.resolve(false, List.of("172.64.0.0/13"), "172.64.10.1", "1.2.3.4"));
  }

  @Test
  void cidrContainsIpv4AndRejectsGarbage() {
    assertTrue(Cidrs.contains("172.64.0.0/13", "172.64.10.1"));
    assertFalse(Cidrs.contains("172.64.0.0/13", "203.0.113.9"));
    assertFalse(Cidrs.contains("not-a-cidr", "1.2.3.4"));
  }
}
