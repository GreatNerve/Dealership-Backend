package com.dealership.dealership;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ServiceSlots {

  public record WeekHours(int weekday, boolean closed, LocalTime open, LocalTime close) {}

  public record OverrideWindow(
      LocalDate fromDate, LocalDate toDate, LocalTime fromTime, LocalTime toTime, int capacity) {}

  private ServiceSlots() {}

  public static boolean aligned(Instant utc, ZoneId zone, int durationMinutes) {
    ZonedDateTime z = utc.atZone(zone);
    if (z.getSecond() != 0 || z.getNano() != 0) {
      return false;
    }
    int minutes = z.getHour() * 60 + z.getMinute();
    return minutes % durationMinutes == 0;
  }

  public static Instant advanceExclusiveEnd(Instant now, ZoneId zone, int maxAdvanceDays) {
    LocalDate today = now.atZone(zone).toLocalDate();
    return today.plusDays(maxAdvanceDays + 1L).atStartOfDay(zone).toInstant();
  }

  public static boolean tooFarAhead(Instant slot, Instant now, ZoneId zone, int maxAdvanceDays) {
    return !slot.isBefore(advanceExclusiveEnd(now, zone, maxAdvanceDays));
  }

  public static boolean allDay(WeekHours hours) {
    return !hours.closed()
        && hours.open() != null
        && hours.close() != null
        && hours.open().equals(LocalTime.MIN)
        && hours.close().equals(LocalTime.MIN);
  }

  public static boolean insideHours(ZonedDateTime local, List<WeekHours> hours) {
    int weekday = local.getDayOfWeek().getValue();
    WeekHours row = null;
    for (WeekHours h : hours) {
      if (h.weekday() == weekday) {
        row = h;
        break;
      }
    }
    if (row == null || row.closed()) {
      return false;
    }
    if (allDay(row)) {
      return true;
    }
    LocalTime t = local.toLocalTime();
    return !t.isBefore(row.open()) && t.isBefore(row.close());
  }

  public static boolean covers(OverrideWindow override, LocalDate date, LocalTime time) {
    if (date.isBefore(override.fromDate()) || date.isAfter(override.toDate())) {
      return false;
    }
    if (override.fromTime() == null) {
      return true;
    }
    return !time.isBefore(override.fromTime()) && time.isBefore(override.toTime());
  }

  public static int effectiveCapacity(
      ZonedDateTime local, int defaultCapacity, List<OverrideWindow> overrides) {
    for (OverrideWindow override : overrides) {
      if (covers(override, local.toLocalDate(), local.toLocalTime())) {
        return override.capacity();
      }
    }
    return defaultCapacity;
  }

  public static boolean overlaps(OverrideWindow a, OverrideWindow b) {
    if (a.toDate().isBefore(b.fromDate()) || b.toDate().isBefore(a.fromDate())) {
      return false;
    }
    if (a.fromTime() == null || b.fromTime() == null) {
      return true;
    }
    return a.fromTime().isBefore(b.toTime()) && b.fromTime().isBefore(a.toTime());
  }

  public static List<Instant> startsOnDay(
      LocalDate day, ZoneId zone, WeekHours hours, int durationMinutes) {
    if (hours.closed()) {
      return List.of();
    }
    List<Instant> out = new ArrayList<>();
    LocalTime start = allDay(hours) ? LocalTime.MIN : hours.open();
    LocalTime endExclusive = allDay(hours) ? null : hours.close();
    while (true) {
      if (endExclusive != null && !start.isBefore(endExclusive)) {
        break;
      }
      out.add(day.atTime(start).atZone(zone).toInstant());
      LocalTime next = start.plusMinutes(durationMinutes);
      if (!next.isAfter(start)) {
        break;
      }
      start = next;
    }
    return out;
  }
}
