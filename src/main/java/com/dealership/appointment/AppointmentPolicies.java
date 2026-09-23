package com.dealership.appointment;

import java.time.Instant;

public final class AppointmentPolicies {

  private AppointmentPolicies() {}

  public static boolean scheduledAtIsPast(Instant scheduledAt, Instant now) {
    return !scheduledAt.isAfter(now);
  }

  /** Reschedule must change the Instant — same wall time is a no-op bump of schedule_version. */
  public static boolean scheduledAtUnchanged(Instant current, Instant next) {
    return current.equals(next);
  }

  public static boolean vehicleAlreadyHasConfirmed(boolean confirmedExists) {
    return confirmedExists;
  }
}
