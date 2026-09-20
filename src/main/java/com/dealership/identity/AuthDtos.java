package com.dealership.identity;

import com.dealership.customer.CustomerDtos;
import com.dealership.dealership.DealershipDtos;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Size(min = 8, max = 100) String password,
      @NotNull Role role) {}

  public record LoginRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Size(min = 8, max = 100) String password) {}

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn) {}

  public record UserResponse(
      UUID id,
      String email,
      Role role,
      UUID homeDealershipId,
      UUID customerId,
      DealershipDtos.DealershipResponse homeDealership,
      CustomerDtos.CustomerSummary customer) {}
}
