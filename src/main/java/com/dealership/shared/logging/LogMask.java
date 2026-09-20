package com.dealership.shared.logging;

public final class LogMask {

  private LogMask() {}

  public static String email(String contact) {
    if (contact == null || contact.isBlank()) {
      return "***";
    }
    int at = contact.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    return contact.charAt(0) + "***" + contact.substring(at);
  }

  public static String vehicleNumber(String registrationNumber) {
    if (registrationNumber == null || registrationNumber.length() < 5) {
      return "***";
    }
    return registrationNumber.substring(0, 2)
        + "…"
        + registrationNumber.substring(registrationNumber.length() - 2);
  }
}
