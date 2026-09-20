package com.dealership.appointment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyScheduler {

  private final IdempotencyService idempotency;

  public IdempotencyScheduler(IdempotencyService idempotency) {
    this.idempotency = idempotency;
  }

  // UTC so EC2 host zone does not pick local midnight
  @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
  public void purgeExpired() {
    idempotency.purgeExpired();
  }
}
