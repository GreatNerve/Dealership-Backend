package com.dealership.vehicle;

import com.dealership.customer.CustomerEntity;
import com.dealership.customer.CustomerRepository;
import com.dealership.identity.Role;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageQuery;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import com.dealership.vehicle.VehicleDtos.CreateVehicleRequest;
import com.dealership.vehicle.VehicleDtos.VehicleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicle")
public class VehicleController {

  private final VehicleService vehicleService;
  private final VehicleRepository vehicles;
  private final CustomerRepository customers;
  private final PageQueries pages;

  public VehicleController(
      VehicleService vehicleService,
      VehicleRepository vehicles,
      CustomerRepository customers,
      PageQueries pages) {
    this.vehicleService = vehicleService;
    this.vehicles = vehicles;
    this.customers = customers;
    this.pages = pages;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a Vehicle")
  public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest request) {
    CustomerEntity customer = requireCustomer();
    return vehicleService.create(customer, request);
  }

  @GetMapping
  @Operation(summary = "List own Vehicles")
  public PageResponse<VehicleResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q) {
    CustomerEntity customer = requireCustomer();
    PageQuery query = pages.bind(page, size, q);
    return PageResponse.of(
        vehicles
            .searchOwn(customer.getId(), query.like(), pages.pageable(query))
            .map(v -> VehicleResponse.from(v, customer)));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get own Vehicle")
  public VehicleResponse get(@PathVariable UUID id) {
    CustomerEntity customer = requireCustomer();
    VehicleEntity vehicle =
        vehicles.findByIdAndCustomerId(id, customer.getId()).orElseThrow(ApiException::notFound);
    return VehicleResponse.from(vehicle, customer);
  }

  private CustomerEntity requireCustomer() {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.CUSTOMER) {
      throw ApiException.forbidden("Only a Customer can manage Vehicles");
    }
    return customers.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
  }
}
