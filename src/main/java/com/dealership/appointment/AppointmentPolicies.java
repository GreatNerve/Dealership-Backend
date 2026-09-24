package com.dealership.appointment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class AppointmentPolicies {

  private AppointmentPolicies() {}

  public static boolean scheduledAtIsPast(Instant scheduledAt, Instant now) {
    return !scheduledAt.isAfter(now);
  }

  /** Reschedule must change the Instant — same wall time is a no-op bump of schedule_version. */
  public static boolean scheduledAtUnchanged(Instant current, Instant next) {
    // timestamptz is microseconds; Instant.equals would miss a no-op after round-trip.
    return current.truncatedTo(ChronoUnit.MICROS).equals(next.truncatedTo(ChronoUnit.MICROS));
  }

  public static boolean vehicleAlreadyHasConfirmed(boolean confirmedExists) {
    return confirmedExists;
  }
}
