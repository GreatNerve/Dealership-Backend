package com.dealership.notification.smtp;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryPolicy {

  public static final int MAX_ATTEMPTS = 5;

  private RetryPolicy() {}

  public static boolean permanent(NotificationFailedException ex) {
    return !ex.transientFailure();
  }

  public static boolean deadLetter(int attemptsIncludingThis) {
    return attemptsIncludingThis >= MAX_ATTEMPTS;
  }

  public static Instant nextAttempt(Instant now, int attemptNumber) {
    // 30s, 60s, 120s, 240s + jitter; cap 5 minutes.
    int n = Math.max(1, attemptNumber);
    long baseSeconds = 30L * (1L << Math.min(n - 1, 3));
    long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseSeconds / 5 + 1));
    long capped = Math.min(300, baseSeconds + jitter);
    return now.plus(Duration.ofSeconds(capped));
  }
}
