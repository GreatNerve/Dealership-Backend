package com.dealership.dealership;

import com.dealership.identity.Role;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.InstantRange;
import com.dealership.shared.api.StatsBucket;
import com.dealership.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

  private final DashboardService dashboard;

  public DashboardController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/stats")
  @Operation(summary = "Home Dealership Appointment and Notification counts with buckets")
  public DashboardDtos.Stats stats(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "DAY") StatsBucket bucket) {
    if (CurrentUser.require().role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can read dashboard stats");
    }
    return dashboard.stats(InstantRange.required(from, to), bucket);
  }
}
