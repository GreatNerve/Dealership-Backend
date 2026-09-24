package com.dealership.shared.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public enum StatsBucket {
  DAY,
  WEEK,
  MONTH;

  /** Year-to-date DAY is ≤366; cap stops unbounded zero-fill. */
  public static final int MAX_SLICES = 400;

  public String unit() {
    return switch (this) {
      case DAY -> "day";
      case WEEK -> "week";
      case MONTH -> "month";
    };
  }

  public LocalDate startOf(LocalDate day) {
    return switch (this) {
      case DAY -> day;
      case WEEK -> day.with(DayOfWeek.MONDAY);
      case MONTH -> day.withDayOfMonth(1);
    };
  }

  public LocalDate next(LocalDate start) {
    return switch (this) {
      case DAY -> start.plusDays(1);
      case WEEK -> start.plusWeeks(1);
      case MONTH -> start.plusMonths(1);
    };
  }

  public void requireFit(Instant from, Instant to, ZoneId zone) {
    if (sliceCount(from, to, zone) > MAX_SLICES) {
      throw ApiException.of(
          ApiErrorCode.VALIDATION_ERROR,
          "from/to spans more than " + MAX_SLICES + " " + name().toLowerCase() + " buckets");
    }
  }

  public long sliceCount(Instant from, Instant to, ZoneId zone) {
    LocalDate start = startOf(from.atZone(zone).toLocalDate());
    LocalDate last = startOf(to.minusNanos(1).atZone(zone).toLocalDate());
    if (last.isBefore(start)) {
      return 0;
    }
    return switch (this) {
      case DAY -> ChronoUnit.DAYS.between(start, last) + 1;
      case WEEK -> ChronoUnit.WEEKS.between(start, last) + 1;
      case MONTH -> ChronoUnit.MONTHS.between(start, last) + 1;
    };
  }

  /** Inclusive period starts covering `[from, to)` in the shop zone. */
  public List<LocalDate> slices(Instant from, Instant to, ZoneId zone) {
    requireFit(from, to, zone);
    LocalDate start = startOf(from.atZone(zone).toLocalDate());
    LocalDate last = startOf(to.minusNanos(1).atZone(zone).toLocalDate());
    List<LocalDate> out = new ArrayList<>();
    for (LocalDate day = start; !day.isAfter(last); day = next(day)) {
      out.add(day);
    }
    return out;
  }

  public <T> List<T> fill(
      Instant from,
      Instant to,
      ZoneId zone,
      Map<LocalDate, T> found,
      Function<LocalDate, T> empty) {
    List<T> out = new ArrayList<>();
    for (LocalDate day : slices(from, to, zone)) {
      T row = found.get(day);
      out.add(row != null ? row : empty.apply(day));
    }
    return out;
  }
}
