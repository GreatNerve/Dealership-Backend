package com.dealership.shared.api;

public record ApiResponse<T>(
    boolean success, T data, ApiErrorCode error, String message, String correlationId) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, CorrelationIds.current());
  }

  public static <T> ApiResponse<T> fail(ApiErrorCode error, String message) {
    return new ApiResponse<>(false, null, error, message, CorrelationIds.current());
  }
}
