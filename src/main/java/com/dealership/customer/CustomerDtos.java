package com.dealership.customer;

import java.util.UUID;

public final class CustomerDtos {

  private CustomerDtos() {}

  public record CustomerSummary(UUID id, String contact) {
    public static CustomerSummary from(CustomerEntity entity) {
      return new CustomerSummary(entity.getId(), entity.getContact());
    }
  }
}
