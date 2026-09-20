package com.dealership.vehicle;

import com.dealership.customer.CustomerEntity;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import com.dealership.shared.logging.LogMask;
import com.dealership.vehicle.VehicleDtos.CreateVehicleRequest;
import com.dealership.vehicle.VehicleDtos.VehicleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

  private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

  private final VehicleRepository vehicles;

  public VehicleService(VehicleRepository vehicles) {
    this.vehicles = vehicles;
  }

  @Transactional
  public VehicleResponse create(CustomerEntity customer, CreateVehicleRequest request) {
    String registrationNumber = VehicleNumbers.normalize(request.registrationNumber());
    if (vehicles.existsByRegistrationNumber(registrationNumber)) {
      throw ApiException.of(ApiErrorCode.REGISTRATION_TAKEN, "Vehicle number already registered");
    }
    VehicleEntity vehicle = new VehicleEntity();
    vehicle.setCustomerId(customer.getId());
    vehicle.setRegistrationNumber(registrationNumber);
    vehicle.setMake(Inputs.sanitize(request.make()));
    vehicle.setModel(Inputs.sanitize(request.model()));
    vehicle.setYear(request.year());
    vehicles.save(vehicle);
    log.info("vehicle created registration={}", LogMask.vehicleNumber(registrationNumber));
    return VehicleResponse.from(vehicle, customer);
  }
}
