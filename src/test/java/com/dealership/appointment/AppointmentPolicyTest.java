package com.dealership.appointment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.shared.time.BookingTimes;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppointmentPolicyTest {

  @Test
  void pastScheduledAtIsRejected() {
    Instant now = Instant.parse("2026-09-22T16:30:00Z");
    var booking = BookingTimes.parseScheduledAt("2026-09-22T22:00:00+05:30");
    assertTrue(AppointmentPolicies.scheduledAtIsPast(booking.utc(), now));
  }

  @Test
  void futureScheduledAtIsAccepted() {
    Instant now = Instant.parse("2026-09-21T00:00:00Z");
    var booking = BookingTimes.parseScheduledAt("2026-09-22T22:00:00+05:30");
    assertFalse(AppointmentPolicies.scheduledAtIsPast(booking.utc(), now));
  }

  @Test
  void sameInstantIsUnchangedForReschedule() {
    Instant t = Instant.parse("2026-09-22T16:30:00.000000123Z");
    assertTrue(AppointmentPolicies.scheduledAtUnchanged(t, t));
    assertTrue(
        AppointmentPolicies.scheduledAtUnchanged(
            t.truncatedTo(java.time.temporal.ChronoUnit.MICROS), t));
    assertFalse(AppointmentPolicies.scheduledAtUnchanged(t, t.plusSeconds(60)));
  }

  @Test
  void secondConfirmedOnSameVehicleConflicts() {
    assertTrue(AppointmentPolicies.vehicleAlreadyHasConfirmed(true));
    assertFalse(AppointmentPolicies.vehicleAlreadyHasConfirmed(false));
  }

  @Test
  void twoVehiclesMayEachHaveConfirmed() {
    assertFalse(AppointmentPolicies.vehicleAlreadyHasConfirmed(false));
  }
}
