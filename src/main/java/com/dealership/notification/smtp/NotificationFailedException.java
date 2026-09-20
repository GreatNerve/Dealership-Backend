package com.dealership.notification.smtp;

public class NotificationFailedException extends Exception {

  private final boolean transientFailure;

  public NotificationFailedException(String message, boolean transientFailure) {
    super(message);
    this.transientFailure = transientFailure;
  }

  public NotificationFailedException(String message, boolean transientFailure, Throwable cause) {
    super(message, cause);
    this.transientFailure = transientFailure;
  }

  public boolean transientFailure() {
    return transientFailure;
  }
}
