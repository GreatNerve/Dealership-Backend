package com.dealership.appointment;

import com.dealership.customer.CustomerDtos;
import com.dealership.dealership.DealershipDtos;
import com.dealership.identity.Role;
import com.dealership.notification.NotificationStatus;
import com.dealership.reminder.ReminderStatus;
import com.dealership.vehicle.VehicleDtos;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {

  private AppointmentDtos() {}

  public record CustomerCreateRequest(
      @NotNull UUID vehicleId,
      @NotNull UUID dealershipId,
      @NotBlank @Size(max = 64) @Schema(example = "2026-09-22T22:00:00+05:30") String scheduledAt,
      @Schema(
              description =
                  "false appends logs/notifications.log; true sends email when mode is smtp")
          @JsonProperty("notify")
          Boolean notifyEnabled) {}

  public record StaffCreateRequest(
      @NotNull UUID customerId,
      @NotNull UUID vehicleId,
      @NotBlank @Size(max = 64) @Schema(example = "2026-09-22T22:00:00+05:30") String scheduledAt,
      @Schema(
              description =
                  "false appends logs/notifications.log; true sends email when mode is smtp")
          @JsonProperty("notify")
          Boolean notifyEnabled,
      UUID dealershipId) {}

  public record RescheduleRequest(
      @NotBlank @Size(max = 64) @Schema(example = "2026-09-22T22:00:00+05:30")
          String scheduledAt) {}

  public record AppointmentResponse(
      UUID id,
      UUID customerId,
      UUID vehicleId,
      UUID dealershipId,
      CustomerDtos.CustomerSummary customer,
      VehicleDtos.VehicleResponse vehicle,
      DealershipDtos.DealershipResponse dealership,
      Instant scheduledAt,
      String displayOffset,
      String scheduledAtLocal,
      AppointmentStatus status,
      Role createdByRole,
      @JsonProperty("notify") boolean notifyEnabled) {}

  public record ReminderItem(
      int offsetMinutes,
      Instant dueAt,
      ReminderStatus reminderStatus,
      NotificationView notification) {}

  public record NotificationView(
      UUID id,
      NotificationStatus status,
      int attempts,
      String lastError,
      Instant sentAt,
      Instant nextAttemptAt) {
    public static NotificationView notScheduled() {
      return new NotificationView(null, NotificationStatus.NOT_SCHEDULED, 0, null, null, null);
    }
  }
}
