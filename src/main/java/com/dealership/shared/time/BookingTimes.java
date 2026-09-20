package com.dealership.shared.time;

import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class BookingTimes {

  private static final DateTimeFormatter MAIL =
      DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' h:mm a", Locale.ENGLISH);

  private BookingTimes() {}

  public static BookingInstant parseScheduledAt(String raw) {
    if (raw == null || raw.isBlank()) {
      throw ApiException.of(ApiErrorCode.INVALID_SCHEDULED_AT, "scheduledAt is required");
    }
    try {
      OffsetDateTime odt = OffsetDateTime.parse(raw);
      return new BookingInstant(odt.toInstant(), odt.getOffset());
    } catch (DateTimeParseException ex) {
      throw ApiException.of(
          ApiErrorCode.INVALID_SCHEDULED_AT, "scheduledAt must be ISO-8601 with a UTC offset");
    }
  }

  public static ZoneOffset parseStoredOffset(String displayOffset) {
    try {
      return ZoneOffset.of(displayOffset);
    } catch (DateTimeException ex) {
      throw ApiException.of(
          ApiErrorCode.INVALID_DISPLAY_OFFSET, "stored display_offset is invalid");
    }
  }

  public static String formatOffsetDateTime(Instant utc, ZoneOffset offset) {
    return utc.atOffset(offset).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  public static String formatStaffLocal(Instant utc, String ianaZone) {
    ZoneId zone = ZoneId.of(ianaZone);
    return ZonedDateTime.ofInstant(utc, zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  public static String formatMail(Instant utc, ZoneOffset offset) {
    OffsetDateTime local = utc.atOffset(offset);
    return local.format(MAIL) + " (UTC" + offset.getId() + ")";
  }

  public static String formatMail(Instant utc, String displayOffset) {
    return formatMail(utc, parseStoredOffset(displayOffset));
  }
}
