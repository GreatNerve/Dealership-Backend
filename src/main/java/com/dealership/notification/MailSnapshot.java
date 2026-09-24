package com.dealership.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record MailSnapshot(
    UUID reminderId,
    UUID notificationId,
    UUID appointmentId,
    UUID dealershipId,
    Integer offsetMinutes,
    Integer scheduleVersion,
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
    int attempts,
    NotificationGeneration generation,
    String subject,
    String body) {

  public boolean fileOnly() {
    return generation == NotificationGeneration.SYSTEM && Boolean.FALSE.equals(notifyEnabled);
  }

  public boolean manual() {
    return generation == NotificationGeneration.MANUAL;
  }

  public String offsetLabel() {
    if (offsetMinutes == null) {
      return "manual";
    }
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
