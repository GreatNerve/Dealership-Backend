package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class GlobalExceptionHandlerTest {

  @Test
  void optimisticLockIsConflict() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    var response =
        handler.handleStale(
            new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID()));
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(ApiErrorCode.CONCURRENT_UPDATE, response.getBody().error());
  }
}
