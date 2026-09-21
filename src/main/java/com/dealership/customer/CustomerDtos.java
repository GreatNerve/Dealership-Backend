package com.dealership.customer;

import java.util.UUID;

public final class CustomerDtos {

  private CustomerDtos() {}

  public record CustomerSummary(UUID id, String contact, String name) {
    public static CustomerSummary from(CustomerEntity entity) {
      return from(entity, null);
    }

    public static CustomerSummary from(CustomerEntity entity, String name) {
      return new CustomerSummary(entity.getId(), entity.getContact(), name);
    }
  }
}
