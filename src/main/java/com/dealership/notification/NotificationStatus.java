package com.dealership.notification;

public enum NotificationStatus {
  PENDING,
  PROCESSING,
  RETRY_SCHEDULED,
  SENT,
  DEAD_LETTER,
  CANCELLED,
  // GET only; PostgreSQL notification_status must not gain this value
  NOT_SCHEDULED
}
