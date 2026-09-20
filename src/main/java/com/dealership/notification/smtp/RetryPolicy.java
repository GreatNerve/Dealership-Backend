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
    long baseSeconds = (long) Math.pow(2, Math.min(attemptNumber, 6));
    long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseSeconds / 2 + 1));
    long capped = Math.min(300, baseSeconds + jitter);
    return now.plus(Duration.ofSeconds(capped));
  }
}
