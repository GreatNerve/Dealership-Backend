package com.dealership.shared.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// High phase → stop early so claim pollers see the gate before new SKIP LOCKED work.
@Component
public class ShutdownGate implements SmartLifecycle {

  private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);
  private volatile boolean running;

  public boolean acceptingClaims() {
    return acceptingClaims.get();
  }

  @Override
  public void start() {
    acceptingClaims.set(true);
    running = true;
  }

  @Override
  public void stop() {
    acceptingClaims.set(false);
    running = false;
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }
}
