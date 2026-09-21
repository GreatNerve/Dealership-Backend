package com.dealership.notification.smtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void timeoutIsTransientUntilMaxAttempts() {
    var transientEx = new NotificationFailedException("timeout", true);
    assertFalse(RetryPolicy.permanent(transientEx));
    assertFalse(RetryPolicy.deadLetter(1));
    assertTrue(RetryPolicy.deadLetter(5));
  }

  @Test
  void smtpAuthIsPermanent() {
    assertTrue(RetryPolicy.permanent(new NotificationFailedException("smtp auth failed", false)));
  }

  @Test
  void invalidContactIsPermanent() {
    assertTrue(RetryPolicy.permanent(new NotificationFailedException("bad address", false)));
  }

  @Test
  void backoffStaysInsideCap() {
    Instant now = Instant.parse("2026-09-20T00:00:00Z");
    Instant next = RetryPolicy.nextAttempt(now, 1);
    assertTrue(!next.isBefore(now.plusSeconds(30)));
    assertTrue(next.isBefore(now.plusSeconds(301)));
    Instant second = RetryPolicy.nextAttempt(now, 2);
    assertTrue(!second.isBefore(now.plusSeconds(60)));
    assertEquals(5, RetryPolicy.MAX_ATTEMPTS);
  }
}
