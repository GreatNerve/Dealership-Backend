package com.dealership.notification;

public enum NotificationStatus {
  PENDING,
  PROCESSING,
  RETRY_SCHEDULED,
  SENT,
  DEAD_LETTER,
  CANCELLED
}
