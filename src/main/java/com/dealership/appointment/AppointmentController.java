package com.dealership.appointment;

import com.dealership.identity.Role;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Appointment")
public class AppointmentController {

  private final AppointmentService appointments;
  private final PageQueries pages;

  public AppointmentController(AppointmentService appointments, PageQueries pages) {
    this.appointments = appointments;
    this.pages = pages;
  }

  public record CreateAppointmentRequest(
      @NotNull UUID vehicleId,
      UUID dealershipId,
      UUID customerId,
      @NotBlank @Size(max = 64) @Schema(example = "2026-09-22T22:00:00+05:30") String scheduledAt,
      @Schema(
              description =
                  "false appends logs/notifications.log; true sends email when mode is smtp")
          @com.fasterxml.jackson.annotation.JsonProperty("notify")
          Boolean notifyEnabled) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create an Appointment (Customer or Staff body)")
  public AppointmentDtos.AppointmentResponse create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateAppointmentRequest body) {
    if (CurrentUser.require().role() == Role.DEALERSHIP_STAFF) {
      return appointments.createStaff(
          idempotencyKey,
          new AppointmentDtos.StaffCreateRequest(
              body.customerId(),
              body.vehicleId(),
              body.scheduledAt(),
              body.notifyEnabled(),
              body.dealershipId()));
    }
    return appointments.createCustomer(
        idempotencyKey,
        new AppointmentDtos.CustomerCreateRequest(
            body.vehicleId(), body.dealershipId(), body.scheduledAt(), body.notifyEnabled()));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an Appointment")
  public AppointmentDtos.AppointmentResponse get(@PathVariable UUID id) {
    return appointments.get(id);
  }

  @GetMapping("/{id}/reminders")
  @Operation(summary = "List Reminders and Notifications for an Appointment (Staff)")
  public List<AppointmentDtos.ReminderItem> reminders(@PathVariable UUID id) {
    return appointments.reminders(id);
  }

  @GetMapping
  @Operation(summary = "List Appointments")
  public PageResponse<AppointmentDtos.AppointmentResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) AppointmentStatus status) {
    return appointments.list(pages.bind(page, size, q), status);
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel a Confirmed Appointment (own or home Dealership)")
  public AppointmentDtos.AppointmentResponse cancel(@PathVariable UUID id) {
    return appointments.cancel(id);
  }

  @PostMapping("/{id}/complete")
  @Operation(summary = "Mark a Confirmed Appointment completed (Staff)")
  public AppointmentDtos.AppointmentResponse complete(@PathVariable UUID id) {
    return appointments.complete(id);
  }

  @PostMapping("/{id}/reschedule")
  @Operation(summary = "Reschedule a Confirmed Appointment (own or home Dealership)")
  public AppointmentDtos.AppointmentResponse reschedule(
      @PathVariable UUID id, @Valid @RequestBody AppointmentDtos.RescheduleRequest body) {
    return appointments.reschedule(id, body);
  }
}
