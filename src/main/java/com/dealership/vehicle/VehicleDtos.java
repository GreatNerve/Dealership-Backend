package com.dealership.vehicle;

import com.dealership.customer.CustomerDtos.CustomerSummary;
import com.dealership.customer.CustomerEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class VehicleDtos {

  private VehicleDtos() {}

  public record CreateVehicleRequest(
      @NotBlank @Size(max = 20) @Schema(example = "KA01AB1234") String registrationNumber,
      @NotBlank @Size(max = 64) @Schema(example = "Honda") String make,
      @NotBlank @Size(max = 64) @Schema(example = "Civic") String model,
      @Min(1950) @Max(2100) @Schema(example = "2022") int year) {}

  public record VehicleResponse(
      UUID id,
      UUID customerId,
      @Schema(example = "KA01AB1234") String registrationNumber,
      @Schema(example = "Honda") String make,
      @Schema(example = "Civic") String model,
      @Schema(example = "2022") int year,
      CustomerSummary customer) {
    public static VehicleResponse from(VehicleEntity entity, CustomerEntity customer) {
      return new VehicleResponse(
          entity.getId(),
          entity.getCustomerId(),
          entity.getRegistrationNumber(),
          entity.getMake(),
          entity.getModel(),
          entity.getYear(),
          CustomerSummary.from(customer));
    }
  }
}
