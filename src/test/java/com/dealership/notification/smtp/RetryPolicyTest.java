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
    assertTrue(!next.isBefore(now.plus(RetryPolicy.MIN_DELAY)));
    assertTrue(next.isBefore(now.plus(RetryPolicy.MAX_DELAY).plusSeconds(1)));
    Instant later = RetryPolicy.nextAttempt(now, 8);
    assertTrue(!later.isAfter(now.plus(RetryPolicy.MAX_DELAY)));
    assertEquals(5, RetryPolicy.MAX_ATTEMPTS);
  }
}
