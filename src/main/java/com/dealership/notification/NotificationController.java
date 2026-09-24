package com.dealership.notification;

import com.dealership.identity.Role;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.ApiResponse;
import com.dealership.shared.api.InstantRange;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.api.StatsBucket;
import com.dealership.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification")
public class NotificationController {

  private final NotificationService notifications;
  private final PageQueries pages;

  public NotificationController(NotificationService notifications, PageQueries pages) {
    this.notifications = notifications;
    this.pages = pages;
  }

  @GetMapping
  @Operation(summary = "List Notifications at the home Dealership")
  public PageResponse<NotificationDtos.NotificationResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) NotificationStatus status,
      @RequestParam(required = false) NotificationGeneration generation,
      @RequestParam(required = false) NotificationChannel channel,
      @RequestParam(required = false) UUID appointmentId,
      @RequestParam(required = false) DeliveryEventType hasEvent) {
    requireStaff();
    if (status == NotificationStatus.NOT_SCHEDULED) {
      throw ApiException.of(
          ApiErrorCode.VALIDATION_ERROR, "status is a stored worker value, not NOT_SCHEDULED");
    }
    return notifications.list(
        pages.bind(page, size, q),
        InstantRange.of(from, to),
        status,
        generation,
        channel,
        appointmentId,
        hasEvent);
  }

  @GetMapping("/stats")
  @Operation(summary = "Notification counts for Instant from/to")
  public NotificationDtos.Stats stats(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) StatsBucket bucket) {
    requireStaff();
    return notifications.stats(InstantRange.required(from, to), bucket);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a Notification and Delivery Events")
  public NotificationDtos.NotificationResponse get(@PathVariable UUID id) {
    requireStaff();
    return notifications.get(id);
  }

  @PostMapping("/{id}/replay")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Replay a dead-lettered Notification")
  public ApiResponse<Void> replay(@PathVariable UUID id) {
    requireStaff();
    notifications.replay(id);
    return ApiResponse.ok(null);
  }

  private static void requireStaff() {
    if (CurrentUser.require().role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can access Notifications");
    }
  }
}
