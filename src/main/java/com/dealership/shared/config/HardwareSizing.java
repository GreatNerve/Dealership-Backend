package com.dealership.shared.config;

public final class HardwareSizing {

  // assignment 50k/day × 10 = 500k; two offsets; 500ms poll; 8-hour day → 18/poll
  static final int TEN_X_CLAIM_FLOOR = 18;
  static final int CLAIM_MAX = 50;
  static final int HIKARI_PER_CPU = 2;
  static final int HIKARI_MIN = 8;
  static final int HIKARI_MAX = 32;
  static final int TOMCAT_PER_CPU = 16;
  static final int TOMCAT_MIN = 50;
  static final int TOMCAT_MAX = 200;

  private HardwareSizing() {}

  public static int cpus() {
    return Math.max(1, Runtime.getRuntime().availableProcessors());
  }

  public static int claimBatch(int cpus) {
    return clamp(Math.max(Math.max(1, cpus), TEN_X_CLAIM_FLOOR), 1, CLAIM_MAX);
  }

  public static int hikariPool(int cpus) {
    return clamp(Math.max(1, cpus) * HIKARI_PER_CPU, HIKARI_MIN, HIKARI_MAX);
  }

  public static int tomcatMax(int cpus) {
    return clamp(Math.max(1, cpus) * TOMCAT_PER_CPU, TOMCAT_MIN, TOMCAT_MAX);
  }

  private static int clamp(int value, int min, int max) {
    return Math.min(max, Math.max(min, value));
  }
}
