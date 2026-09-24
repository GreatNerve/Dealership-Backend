package com.dealership.shared.api;

import java.time.Instant;

public record InstantRange(Instant from, Instant to) {

  public static InstantRange of(Instant from, Instant to) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "from must be before to");
    }
    return new InstantRange(from, to);
  }

  public static InstantRange required(Instant from, Instant to) {
    if (from == null || to == null) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "from and to are required");
    }
    return of(from, to);
  }
}
