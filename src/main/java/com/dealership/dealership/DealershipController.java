package com.dealership.dealership;

import com.dealership.dealership.DealershipDtos.DealershipResponse;
import com.dealership.dealership.DealershipDtos.OverrideResponse;
import com.dealership.dealership.DealershipDtos.OverrideWriteRequest;
import com.dealership.dealership.DealershipDtos.ScheduleResponse;
import com.dealership.dealership.DealershipDtos.ScheduleWriteRequest;
import com.dealership.dealership.DealershipDtos.SlotsResponse;
import com.dealership.shared.api.InstantRange;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageQuery;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dealerships")
@Tag(name = "Dealership")
public class DealershipController {

  private final DealershipRepository dealerships;
  private final DealershipService dealershipService;
  private final ServiceSlotService slots;
  private final PageQueries pages;

  public DealershipController(
      DealershipRepository dealerships,
      DealershipService dealershipService,
      ServiceSlotService slots,
      PageQueries pages) {
    this.dealerships = dealerships;
    this.dealershipService = dealershipService;
    this.slots = slots;
    this.pages = pages;
  }

  public record CreateDealershipRequest(
      @NotBlank @Size(max = 255) String name,
      @NotBlank @Size(max = 64) String timezone,
      @NotBlank @Size(max = 512) String address) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a Dealership; creator becomes its Staff Member")
  public DealershipResponse create(@Valid @RequestBody CreateDealershipRequest request) {
    return dealershipService.create(request.name(), request.timezone(), request.address());
  }

  @GetMapping
  @Operation(summary = "List Dealerships")
  public PageResponse<DealershipResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q) {
    CurrentUser.require();
    PageQuery query = pages.bind(page, size, q);
    return PageResponse.of(
        dealerships
            .findAll(DealershipRepository.matching(query.like()), pages.pageable(query))
            .map(DealershipResponse::from));
  }

  @GetMapping("/{id}/schedule")
  @Operation(summary = "Weekly hours and default Slot Capacity")
  public ScheduleResponse schedule(@PathVariable UUID id) {
    return slots.getSchedule(id);
  }

  @PutMapping("/{id}/schedule")
  @Operation(summary = "Replace weekly hours and default Slot Capacity (Staff, home)")
  public ScheduleResponse putSchedule(
      @PathVariable UUID id, @RequestBody ScheduleWriteRequest body) {
    return slots.putSchedule(id, body);
  }

  @GetMapping("/{id}/overrides")
  @Operation(summary = "List Capacity Overrides (Staff, home)")
  public List<OverrideResponse> overrides(@PathVariable UUID id) {
    return slots.listOverrides(id);
  }

  @PostMapping("/{id}/overrides")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a Capacity Override (Staff, home)")
  public OverrideResponse createOverride(
      @PathVariable UUID id, @RequestBody OverrideWriteRequest body) {
    return slots.createOverride(id, body);
  }

  @PatchMapping("/{id}/overrides/{overrideId}")
  @Operation(summary = "Update a Capacity Override (Staff, home)")
  public OverrideResponse updateOverride(
      @PathVariable UUID id,
      @PathVariable UUID overrideId,
      @RequestBody OverrideWriteRequest body) {
    return slots.updateOverride(id, overrideId, body);
  }

  @DeleteMapping("/{id}/overrides/{overrideId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a Capacity Override (Staff, home)")
  public void deleteOverride(@PathVariable UUID id, @PathVariable UUID overrideId) {
    slots.deleteOverride(id, overrideId);
  }

  @GetMapping("/{id}/slots")
  @Operation(summary = "Service Slots with capacity and booked counts")
  public SlotsResponse slots(
      @PathVariable UUID id, @RequestParam Instant from, @RequestParam Instant to) {
    return slots.listSlots(id, InstantRange.required(from, to));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a Dealership")
  public DealershipResponse get(@PathVariable UUID id) {
    CurrentUser.require();
    return dealerships
        .findById(id)
        .map(DealershipResponse::from)
        .orElseThrow(com.dealership.shared.api.ApiException::notFound);
  }
}
