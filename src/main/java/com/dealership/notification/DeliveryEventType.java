package com.dealership.notification;

public enum DeliveryEventType {
  ACCEPTED,
  DELIVERED,
  SOFT_BOUNCE,
  HARD_BOUNCE,
  OPENED,
  CLICKED,
  SPAM,
  BLOCKED,
  ERROR,
  OTHER;

  // Provider BLOCKED is a reject, same as bounce for Staff counts.
  public static boolean bounce(DeliveryEventType type) {
    return type == SOFT_BOUNCE || type == HARD_BOUNCE || type == BLOCKED;
  }
}
