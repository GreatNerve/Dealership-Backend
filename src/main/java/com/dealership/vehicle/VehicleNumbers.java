package com.dealership.vehicle;

import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import java.util.Locale;

public final class VehicleNumbers {

  private VehicleNumbers() {}

  public static String normalize(String raw) {
    String normalized = Inputs.sanitize(raw);
    if (normalized == null || normalized.isBlank()) {
      throw ApiException.of(ApiErrorCode.INVALID_REGISTRATION, "registrationNumber is required");
    }
    normalized = normalized.toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
    if (!normalized.matches("[A-Z0-9]{8,13}")) {
      throw ApiException.of(ApiErrorCode.INVALID_REGISTRATION, "registrationNumber is invalid");
    }
    return normalized;
  }
}
