package com.dealership.shared.api;

import org.slf4j.MDC;

public final class CorrelationIds {

  private CorrelationIds() {}

  public static String current() {
    String id = MDC.get(CorrelationIdFilter.MDC_KEY);
    return id == null ? "" : id;
  }
}
