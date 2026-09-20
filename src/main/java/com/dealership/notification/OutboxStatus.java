package com.dealership.notification;

public enum OutboxStatus {
  PENDING,
  PROCESSING,
  RETRY_SCHEDULED,
  PUBLISHED
}
