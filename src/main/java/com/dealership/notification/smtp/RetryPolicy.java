package com.dealership.notification.smtp;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryPolicy {

  public static final int MAX_ATTEMPTS = 5;
  // Floor waits for a Brevo webhook (correlation header) before another SMTP attempt.
  public static final Duration MIN_DELAY = Duration.ofMinutes(2);
  public static final Duration MAX_DELAY = Duration.ofMinutes(10);

  private RetryPolicy() {}

  public static boolean permanent(NotificationFailedException ex) {
    return !ex.transientFailure();
  }

  public static boolean deadLetter(int attemptsIncludingThis) {
    return attemptsIncludingThis >= MAX_ATTEMPTS;
  }

  public static Instant nextAttempt(Instant now, int attemptNumber) {
    // 2m, 4m, 8m + jitter; cap 10 minutes.
    int n = Math.max(1, attemptNumber);
    long baseSeconds = MIN_DELAY.toSeconds() * (1L << Math.min(n - 1, 3));
    long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseSeconds / 5 + 1));
    long capped = Math.min(MAX_DELAY.toSeconds(), baseSeconds + jitter);
    return now.plus(Duration.ofSeconds(capped));
  }
}
