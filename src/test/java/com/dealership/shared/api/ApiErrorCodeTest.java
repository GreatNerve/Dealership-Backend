package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiErrorCodeTest {

  @Test
  void timezoneAndAlreadySentAreEnumNotFreeText() {
    assertEquals(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_TIMEZONE.status());
    assertEquals(HttpStatus.CONFLICT, ApiErrorCode.ALREADY_SENT.status());
    assertEquals("INVALID_TIMEZONE", ApiErrorCode.INVALID_TIMEZONE.name());
    assertEquals("ALREADY_SENT", ApiErrorCode.ALREADY_SENT.name());
  }
}
