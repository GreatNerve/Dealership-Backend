package com.dealership.shared.time;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemTimeProvider implements TimeProvider {

  private final Clock clock = Clock.systemUTC();

  @Override
  public Instant now() {
    return Instant.now(clock);
  }
}
