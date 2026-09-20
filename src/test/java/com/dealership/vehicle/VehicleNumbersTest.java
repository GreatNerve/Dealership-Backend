package com.dealership.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import org.junit.jupiter.api.Test;

class VehicleNumbersTest {

  @Test
  void stripsHyphensAndUppercases() {
    assertEquals("KA01AB1234", VehicleNumbers.normalize("ka-01-ab-1234"));
  }

  @Test
  void sanitizesThenNormalizes() {
    assertEquals("KA01AB1234", VehicleNumbers.normalize("  ka-01-\u0000ab-1234  "));
  }

  @Test
  void rejectsShortJunk() {
    ApiException ex = assertThrows(ApiException.class, () -> VehicleNumbers.normalize("KA1"));
    assertEquals(ApiErrorCode.INVALID_REGISTRATION, ex.error());
    assertEquals("registrationNumber is invalid", ex.getMessage());
  }
}
