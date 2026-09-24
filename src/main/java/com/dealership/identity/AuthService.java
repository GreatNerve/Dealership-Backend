package com.dealership.identity;

import com.dealership.customer.CustomerDtos;
import com.dealership.customer.CustomerEntity;
import com.dealership.customer.CustomerRepository;
import com.dealership.dealership.DealershipDtos;
import com.dealership.dealership.DealershipRepository;
import com.dealership.dealership.DealershipStaffRepository;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import com.dealership.shared.security.JwtService;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final CustomerRepository customers;
  private final DealershipRepository dealerships;
  private final DealershipStaffRepository staff;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      CustomerRepository customers,
      DealershipRepository dealerships,
      DealershipStaffRepository staff,
      PasswordEncoder passwords,
      JwtService jwt) {
    this.users = users;
    this.customers = customers;
    this.dealerships = dealerships;
    this.staff = staff;
    this.passwords = passwords;
    this.jwt = jwt;
    this.dummyHash = passwords.encode("login-miss");
  }

  @Transactional
  public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
    return createUser(request.email(), request.name(), request.password(), request.role());
  }

  @Transactional
  public UUID createCustomer(String email, String name, String password) {
    var user = createUser(email, name, password, Role.CUSTOMER);
    return customers.findByUserId(user.id()).orElseThrow(ApiException::notFound).getId();
  }

  private AuthDtos.UserResponse createUser(
      String rawEmail, String rawName, String password, Role role) {
    String email = Inputs.email(rawEmail);
    if (users.existsByEmail(email)) {
      throw ApiException.of(ApiErrorCode.EMAIL_TAKEN, "Email already registered");
    }
    UserEntity user = new UserEntity();
    user.setEmail(email);
    user.setName(blankToNull(rawName));
    user.setPasswordHash(passwords.encode(Inputs.sanitize(password)));
    user.setRole(role);
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.of(ApiErrorCode.EMAIL_TAKEN, "Email already registered");
    }
    if (role == Role.CUSTOMER) {
      CustomerEntity customer = new CustomerEntity();
      customer.setUserId(user.getId());
      customer.setContact(email);
      customers.save(customer);
    }
    return toResponse(user);
  }

  public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
    String email = Inputs.email(request.email());
    String password = Inputs.sanitize(request.password());
    UserEntity user = users.findByEmail(email).orElse(null);
    boolean ok =
        Credentials.matches(
            passwords, password, user == null ? null : user.getPasswordHash(), dummyHash);
    if (user == null || !ok) {
      throw ApiException.unauthorized("Invalid credentials");
    }
    String token = jwt.issue(user.getId(), user.getEmail(), user.getRole());
    return new AuthDtos.TokenResponse(token, "Bearer", jwt.expiresInSeconds());
  }

  @Transactional(readOnly = true)
  public AuthDtos.UserResponse me() {
    AuthPrincipal principal = CurrentUser.require();
    UserEntity user = users.findById(principal.userId()).orElseThrow(ApiException::notFound);
    return toResponse(user);
  }

  private AuthDtos.UserResponse toResponse(UserEntity user) {
    if (user.getRole() == Role.CUSTOMER) {
      CustomerEntity customer = customers.findByUserId(user.getId()).orElse(null);
      return new AuthDtos.UserResponse(
          user.getId(),
          user.getEmail(),
          user.getName(),
          user.getRole(),
          null,
          customer != null ? customer.getId() : null,
          null,
          customer != null ? CustomerDtos.CustomerSummary.from(customer, user.getName()) : null);
    }
    var membership = staff.findByUserId(user.getId());
    UUID homeId = membership.map(row -> row.getDealershipId()).orElse(null);
    var home =
        membership
            .flatMap(row -> dealerships.findById(row.getDealershipId()))
            .map(DealershipDtos.DealershipResponse::from)
            .orElse(null);
    return new AuthDtos.UserResponse(
        user.getId(), user.getEmail(), user.getName(), user.getRole(), homeId, null, home, null);
  }

  private static String blankToNull(String raw) {
    String cleaned = Inputs.sanitize(raw);
    return cleaned == null || cleaned.isEmpty() ? null : cleaned;
  }
}
