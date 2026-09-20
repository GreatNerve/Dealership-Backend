package com.dealership.notification;

import com.dealership.identity.Role;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.ApiResponse;
import com.dealership.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification")
public class NotificationController {

  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @PostMapping("/{id}/replay")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Replay a dead-lettered Notification")
  public ApiResponse<Void> replay(@PathVariable UUID id) {
    if (CurrentUser.require().role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can replay Notifications");
    }
    notifications.replay(id);
    return ApiResponse.ok(null);
  }
}
