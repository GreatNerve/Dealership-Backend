package com.dealership.notification;

import com.dealership.appointment.AppointmentDtos;
import com.dealership.shared.api.MultilineStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

  private NotificationDtos() {}

  public record ManualSendRequest(
      @NotBlank @Size(max = 255) String subject,
      @NotBlank @Size(max = 8000) @JsonDeserialize(using = MultilineStringDeserializer.class)
          String body) {}

  public record NotificationResponse(
      UUID id,
      UUID dealershipId,
      UUID appointmentId,
      UUID reminderId,
      Integer offsetMinutes,
      NotificationChannel channel,
      NotificationGeneration generation,
      NotificationStatus status,
      int attempts,
      String lastError,
      Instant sentAt,
      Instant nextAttemptAt,
      String subject,
      String body,
      boolean opened,
      boolean bounced,
      List<DeliveryEventView> events,
      AppointmentDtos.AppointmentResponse appointment) {}

  public record DeliveryEventView(
      DeliveryEventType eventType, Instant occurredAt, DeliveryProvider provider) {}

  public record Stats(
      long appointments,
      long notificationsSent,
      long opened,
      long softBounce,
      long hardBounce,
      long failed,
      long bounced,
      List<DailyStats> buckets) {}

  public record DailyStats(
      java.time.LocalDate date, long sent, long failed, long bounced, long opened) {}
}
