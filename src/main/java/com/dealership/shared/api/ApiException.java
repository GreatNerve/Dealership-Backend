package com.dealership.shared.api;

import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {

  private final ApiErrorCode error;

  private ApiException(ApiErrorCode error, String message) {
    super(message);
    this.error = error;
  }

  public HttpStatus status() {
    return error.status();
  }

  public ApiErrorCode error() {
    return error;
  }

  public static ApiException of(ApiErrorCode error, String message) {
    return new ApiException(error, message);
  }

  public static ApiException unauthorized(String message) {
    return of(ApiErrorCode.UNAUTHORIZED, message);
  }

  public static ApiException forbidden(String message) {
    return of(ApiErrorCode.FORBIDDEN, message);
  }

  public static ApiException notFound() {
    return of(ApiErrorCode.NOT_FOUND, "Resource not found");
  }

  public static ApiException retryable(String message) {
    return of(ApiErrorCode.RETRYABLE, message);
  }
}
