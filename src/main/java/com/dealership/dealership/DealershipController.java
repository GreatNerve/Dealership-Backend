package com.dealership.dealership;

import com.dealership.dealership.DealershipDtos.DealershipResponse;
import com.dealership.identity.Role;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageQuery;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final DealershipStaffRepository staff;
  private final PageQueries pages;

  public DealershipController(
      DealershipRepository dealerships, DealershipStaffRepository staff, PageQueries pages) {
    this.dealerships = dealerships;
    this.staff = staff;
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
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can create a Dealership");
    }
    if (staff.existsByUserId(user.userId())) {
      throw ApiException.of(
          ApiErrorCode.HOME_DEALERSHIP_EXISTS, "Staff Member already has a home Dealership");
    }
    String timezone = Inputs.sanitize(request.timezone());
    try {
      ZoneId.of(timezone);
    } catch (DateTimeException ex) {
      throw ApiException.of(ApiErrorCode.INVALID_TIMEZONE, "timezone must be an IANA id");
    }
    DealershipEntity shop = new DealershipEntity();
    shop.setName(Inputs.sanitize(request.name()));
    shop.setTimezone(timezone);
    shop.setAddress(Inputs.sanitize(request.address()));
    dealerships.save(shop);
    DealershipStaffEntity membership = new DealershipStaffEntity();
    membership.setUserId(user.userId());
    membership.setDealershipId(shop.getId());
    staff.save(membership);
    return DealershipResponse.from(shop);
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

  @GetMapping("/{id}")
  @Operation(summary = "Get a Dealership")
  public DealershipResponse get(@PathVariable UUID id) {
    CurrentUser.require();
    return dealerships
        .findById(id)
        .map(DealershipResponse::from)
        .orElseThrow(ApiException::notFound);
  }
}
