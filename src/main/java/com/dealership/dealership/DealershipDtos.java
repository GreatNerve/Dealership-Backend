package com.dealership.dealership;

import java.util.UUID;

public final class DealershipDtos {

  private DealershipDtos() {}

  public record DealershipResponse(UUID id, String name, String timezone, String address) {
    public static DealershipResponse from(DealershipEntity entity) {
      return new DealershipResponse(
          entity.getId(), entity.getName(), entity.getTimezone(), entity.getAddress());
    }
  }
}
