package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsBucketTest {

  private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

  @Test
  void daySlicesOneShopDay() {
    Instant from = Instant.parse("2026-09-23T18:30:00Z");
    Instant to = Instant.parse("2026-09-24T18:30:00Z");
    assertEquals(List.of(LocalDate.of(2026, 9, 24)), StatsBucket.DAY.slices(from, to, KOLKATA));
  }

  @Test
  void daySlicesSameLocalDayWhenToIsNotMidnight() {
    Instant from = Instant.parse("2026-09-24T04:30:00Z");
    Instant to = Instant.parse("2026-09-24T05:30:00Z");
    assertEquals(List.of(LocalDate.of(2026, 9, 24)), StatsBucket.DAY.slices(from, to, KOLKATA));
  }

  @Test
  void weekSlicesMondayStarts() {
    Instant from = Instant.parse("2026-09-17T18:30:00Z");
    Instant to = Instant.parse("2026-09-24T18:30:00Z");
    assertEquals(
        List.of(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21)),
        StatsBucket.WEEK.slices(from, to, KOLKATA));
  }

  @Test
  void monthStartAndNext() {
    assertEquals(LocalDate.of(2026, 9, 1), StatsBucket.MONTH.startOf(LocalDate.of(2026, 9, 24)));
    assertEquals(LocalDate.of(2026, 10, 1), StatsBucket.MONTH.next(LocalDate.of(2026, 9, 1)));
  }

  @Test
  void dayRangeOverMaxSlicesIsInvalid() {
    Instant from = Instant.parse("2000-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-09-24T00:00:00Z");
    ApiException ex =
        assertThrows(ApiException.class, () -> StatsBucket.DAY.requireFit(from, to, KOLKATA));
    assertEquals(ApiErrorCode.VALIDATION_ERROR, ex.error());
  }
}
