package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InstantRangeTest {

  @Test
  void fromAfterToIsInvalid() {
    Instant from = Instant.parse("2026-09-24T12:00:00Z");
    Instant to = Instant.parse("2026-09-24T11:00:00Z");
    ApiException ex = assertThrows(ApiException.class, () -> InstantRange.of(from, to));
    assertEquals(ApiErrorCode.VALIDATION_ERROR, ex.error());
  }

  @Test
  void fromEqualsToIsInvalid() {
    Instant ts = Instant.parse("2026-09-24T12:00:00Z");
    ApiException ex = assertThrows(ApiException.class, () -> InstantRange.of(ts, ts));
    assertEquals(ApiErrorCode.VALIDATION_ERROR, ex.error());
  }

  @Test
  void statsRequireBothBounds() {
    ApiException ex =
        assertThrows(ApiException.class, () -> InstantRange.required(Instant.now(), null));
    assertEquals(ApiErrorCode.VALIDATION_ERROR, ex.error());
  }
}
