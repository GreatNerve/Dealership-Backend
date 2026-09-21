package com.dealership.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record MailSnapshot(
    UUID reminderId,
    UUID appointmentId,
    int offsetMinutes,
    int scheduleVersion,
    Instant scheduledAt,
    String displayOffset,
    String dealershipName,
    String customerName,
    String vehicleMake,
    String vehicleModel,
    Integer vehicleYear,
    String registrationNumber,
    String contact,
    String idempotencyKey,
    Boolean notifyEnabled,
    int attempts) {

  public boolean fileOnly() {
    return Boolean.FALSE.equals(notifyEnabled);
  }

  public String offsetLabel() {
    Duration offset = Duration.ofMinutes(offsetMinutes);
    if (offset.toDays() > 0 && offset.equals(Duration.ofDays(offset.toDays()))) {
      return offset.toDays() + "d";
    }
    if (offset.toHours() > 0 && offset.equals(Duration.ofHours(offset.toHours()))) {
      return offset.toHours() + "h";
    }
    return offsetMinutes + "m";
  }
}
