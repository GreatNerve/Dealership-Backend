package com.dealership.dealership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.dealership.ServiceSlots.OverrideWindow;
import com.dealership.dealership.ServiceSlots.WeekHours;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceSlotsTest {

  private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

  @Test
  void alignedOnHalfHour() {
    Instant slot = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, KOLKATA).toInstant();
    assertTrue(ServiceSlots.aligned(slot, KOLKATA, 30));
    Instant off = ZonedDateTime.of(2026, 10, 5, 10, 7, 0, 0, KOLKATA).toInstant();
    assertFalse(ServiceSlots.aligned(off, KOLKATA, 30));
  }

  @Test
  void maxAdvanceIsEndOfLocalDay() {
    Instant now = ZonedDateTime.of(2026, 9, 29, 10, 0, 0, 0, KOLKATA).toInstant();
    Instant end = ServiceSlots.advanceExclusiveEnd(now, KOLKATA, 15);
    Instant last = ZonedDateTime.of(2026, 10, 14, 23, 30, 0, 0, KOLKATA).toInstant();
    Instant after = ZonedDateTime.of(2026, 10, 15, 0, 0, 0, 0, KOLKATA).toInstant();
    assertFalse(ServiceSlots.tooFarAhead(last, now, KOLKATA, 15));
    assertTrue(ServiceSlots.tooFarAhead(after, now, KOLKATA, 15));
    assertEquals(after, end);
  }

  @Test
  void hoursOpenInclusiveCloseExclusive() {
    List<WeekHours> hours =
        List.of(new WeekHours(1, false, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    ZonedDateTime open = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, KOLKATA);
    ZonedDateTime last = ZonedDateTime.of(2026, 10, 5, 17, 30, 0, 0, KOLKATA);
    ZonedDateTime close = ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, KOLKATA);
    assertTrue(ServiceSlots.insideHours(open, hours));
    assertTrue(ServiceSlots.insideHours(last, hours));
    assertFalse(ServiceSlots.insideHours(close, hours));
  }

  @Test
  void overrideOverlapDateOnlyVsWindow() {
    OverrideWindow holiday =
        new OverrideWindow(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26), null, null, 0);
    OverrideWindow lunch =
        new OverrideWindow(
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 12, 25),
            LocalTime.of(10, 0),
            LocalTime.of(12, 0),
            5);
    assertTrue(ServiceSlots.overlaps(holiday, lunch));
    OverrideWindow morning =
        new OverrideWindow(
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 12, 25),
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            2);
    OverrideWindow afternoon =
        new OverrideWindow(
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 12, 25),
            LocalTime.of(14, 0),
            LocalTime.of(18, 0),
            2);
    assertFalse(ServiceSlots.overlaps(morning, afternoon));
  }

  @Test
  void gridStopsBeforeClose() {
    WeekHours mon = new WeekHours(1, false, LocalTime.of(9, 0), LocalTime.of(10, 0));
    List<Instant> starts = ServiceSlots.startsOnDay(LocalDate.of(2026, 10, 5), KOLKATA, mon, 30);
    assertEquals(2, starts.size());
    assertEquals(ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, KOLKATA).toInstant(), starts.get(0));
    assertEquals(ZonedDateTime.of(2026, 10, 5, 9, 30, 0, 0, KOLKATA).toInstant(), starts.get(1));
  }
}
