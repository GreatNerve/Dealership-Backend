package com.dealership.reminder;

public enum ReminderStatus {
  PENDING,
  PROCESSING,
  RETRY_SCHEDULED,
  SENT,
  DEAD_LETTER,
  CANCELLED,
  EXPIRED
}
