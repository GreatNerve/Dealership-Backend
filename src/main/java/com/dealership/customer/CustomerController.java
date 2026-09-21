package com.dealership.customer;

import com.dealership.dealership.DealershipStaffRepository;
import com.dealership.identity.AuthService;
import com.dealership.identity.Role;
import com.dealership.identity.UserEntity;
import com.dealership.identity.UserRepository;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageQuery;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.security.CurrentUser;
import com.dealership.vehicle.VehicleDtos.CreateVehicleRequest;
import com.dealership.vehicle.VehicleDtos.VehicleResponse;
import com.dealership.vehicle.VehicleEntity;
import com.dealership.vehicle.VehicleRepository;
import com.dealership.vehicle.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer")
public class CustomerController {

  private final CustomerRepository customers;
  private final UserRepository users;
  private final VehicleRepository vehicles;
  private final VehicleService vehicleService;
  private final AuthService auth;
  private final DealershipStaffRepository staff;
  private final PageQueries pages;

  public CustomerController(
      CustomerRepository customers,
      UserRepository users,
      VehicleRepository vehicles,
      VehicleService vehicleService,
      AuthService auth,
      DealershipStaffRepository staff,
      PageQueries pages) {
    this.customers = customers;
    this.users = users;
    this.vehicles = vehicles;
    this.vehicleService = vehicleService;
    this.auth = auth;
    this.staff = staff;
    this.pages = pages;
  }

  public record CustomerResponse(
      UUID id, String contact, String name, List<VehicleResponse> vehicles) {}

  public record CreateCustomerRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @Size(max = 100) String name,
      @NotBlank @Size(min = 8, max = 100) String password) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a walk-in Customer (Staff)")
  public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
    requireStaff();
    UUID customerId = auth.createCustomer(request.email(), request.name(), request.password());
    return get(customerId);
  }

  @GetMapping
  @Operation(summary = "Search Customers and their Vehicles (Staff)")
  public PageResponse<CustomerResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q) {
    requireStaff();
    PageQuery query = pages.bind(page, size, q);
    var result =
        customers.findAll(CustomerRepository.matching(query.like()), pages.pageable(query));
    List<UUID> ids = result.getContent().stream().map(CustomerEntity::getId).toList();
    Map<UUID, String> names = namesByUserId(result.getContent());
    Map<UUID, List<VehicleEntity>> byCustomer =
        ids.isEmpty()
            ? Map.of()
            : vehicles.findByCustomerIdIn(ids).stream()
                .collect(Collectors.groupingBy(VehicleEntity::getCustomerId));
    return PageResponse.of(
        result.map(
            c ->
                new CustomerResponse(
                    c.getId(),
                    c.getContact(),
                    names.get(c.getUserId()),
                    byCustomer.getOrDefault(c.getId(), List.of()).stream()
                        .map(v -> VehicleResponse.from(v, c, names.get(c.getUserId())))
                        .toList())));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a Customer and their Vehicles (Staff)")
  public CustomerResponse get(@PathVariable UUID id) {
    requireStaff();
    CustomerEntity customer = customers.findById(id).orElseThrow(ApiException::notFound);
    List<VehicleResponse> owned =
        vehicles.findByCustomerIdIn(List.of(id)).stream()
            .map(v -> VehicleResponse.from(v, customer, nameOf(customer)))
            .toList();
    return new CustomerResponse(customer.getId(), customer.getContact(), nameOf(customer), owned);
  }

  @GetMapping("/{id}/vehicles")
  @Operation(summary = "List a Customer's Vehicles (Staff)")
  public PageResponse<VehicleResponse> vehicles(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q) {
    requireStaff();
    PageQuery query = pages.bind(page, size, q);
    CustomerEntity customer = customers.findById(id).orElseThrow(ApiException::notFound);
    return PageResponse.of(
        vehicles
            .searchOwn(id, query.like(), pages.pageable(query))
            .map(v -> VehicleResponse.from(v, customer, nameOf(customer))));
  }

  @PostMapping("/{id}/vehicles")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a Vehicle for a Customer (Staff)")
  public VehicleResponse addVehicle(
      @PathVariable UUID id, @Valid @RequestBody CreateVehicleRequest request) {
    requireStaff();
    CustomerEntity customer = customers.findById(id).orElseThrow(ApiException::notFound);
    return vehicleService.create(customer, request);
  }

  private String nameOf(CustomerEntity customer) {
    return users.findById(customer.getUserId()).map(UserEntity::getName).orElse(null);
  }

  private Map<UUID, String> namesByUserId(List<CustomerEntity> rows) {
    Map<UUID, String> names = new HashMap<>();
    if (rows.isEmpty()) {
      return names;
    }
    for (UserEntity user :
        users.findAllById(rows.stream().map(CustomerEntity::getUserId).distinct().toList())) {
      names.put(user.getId(), user.getName());
    }
    return names;
  }

  private void requireStaff() {
    var user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can manage the Customer directory");
    }
    staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
  }
}
