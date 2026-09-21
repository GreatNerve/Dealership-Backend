package com.dealership.shared.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BookingTimesTest {

  @Test
  void parseKeepsOffsetAndUtcInstant() {
    BookingInstant booking = BookingTimes.parseScheduledAt("2026-09-22T22:00:00+05:30");
    assertEquals(Instant.parse("2026-09-22T16:30:00Z"), booking.utc());
    assertEquals(ZoneOffset.of("+05:30"), booking.displayOffset());
  }

  @Test
  void naiveDatetimeRejected() {
    ApiException ex =
        assertThrows(
            ApiException.class, () -> BookingTimes.parseScheduledAt("2026-09-22T22:00:00"));
    assertEquals(ApiErrorCode.INVALID_SCHEDULED_AT, ex.error());
  }

  @Test
  void mailUsesBookingOffsetNotUtc() {
    Instant utc = Instant.parse("2026-09-22T16:30:00Z");
    String mail = BookingTimes.formatMail(utc, "+05:30");
    assertTrue(mail.contains("10:00 PM"));
    assertTrue(mail.contains("UTC+05:30"));
    assertTrue(!mail.contains("16:30"));
  }

  @Test
  void mailClockIsLocalWithoutUtcLabel() {
    Instant utc = Instant.parse("2026-09-22T16:30:00Z");
    assertEquals("10:00 PM", BookingTimes.formatMailClock(utc, "+05:30"));
  }

  @Test
  void staffLocalUsesDealershipZone() {
    Instant utc = Instant.parse("2026-09-22T16:30:00Z");
    String local = BookingTimes.formatStaffLocal(utc, "Asia/Kolkata");
    assertTrue(local.contains("+05:30"));
    assertTrue(local.contains("22:00"));
  }

  @Test
  void formatterDoesNotUseHostZone() {
    ZoneId host = ZoneId.systemDefault();
    Instant utc = Instant.parse("2026-09-22T16:30:00Z");
    String mail = BookingTimes.formatMail(utc, ZoneOffset.of("+05:30"));
    assertTrue(mail.contains("UTC+05:30"));
    assertTrue(!mail.contains(host.getId()));
  }
}
