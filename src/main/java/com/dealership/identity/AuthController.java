package com.dealership.identity;

import com.dealership.shared.api.Inputs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Identity")
@Validated
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  @Operation(summary = "Register a Customer or Staff Member")
  public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirements
  @Operation(summary = "Login and receive a 1-day JWT")
  public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
    return authService.login(request);
  }

  // Swagger Authorize password flow posts form username/password (username = email).
  @PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @SecurityRequirements
  @Operation(hidden = true)
  public AuthDtos.TokenResponse loginForm(
      @RequestParam("username") @NotBlank @Email @Size(max = 320) String username,
      @RequestParam("password") @NotBlank @Size(min = 8, max = 100) String password) {
    return authService.login(
        new AuthDtos.LoginRequest(Inputs.email(username), Inputs.sanitize(password)));
  }

  @GetMapping("/me")
  @Operation(summary = "Current User")
  public AuthDtos.UserResponse me() {
    return authService.me();
  }
}
