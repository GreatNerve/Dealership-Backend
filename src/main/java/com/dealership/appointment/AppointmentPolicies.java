package com.dealership.appointment;

public final class AppointmentPolicies {

  private AppointmentPolicies() {}

  public static boolean scheduledAtIsPast(java.time.Instant scheduledAt, java.time.Instant now) {
    return !scheduledAt.isAfter(now);
  }

  public static boolean vehicleAlreadyHasConfirmed(boolean confirmedExists) {
    return confirmedExists;
  }
}
