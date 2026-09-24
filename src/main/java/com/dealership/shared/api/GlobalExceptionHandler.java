package com.dealership.shared.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
    return fail(ex.error(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + " " + err.getDefaultMessage())
            .orElse("Validation failed");
    return fail(ApiErrorCode.VALIDATION_ERROR, message);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodValid(HandlerMethodValidationException ex) {
    String message =
        ex.getAllErrors().stream()
            .findFirst()
            .map(MessageSourceResolvable::getDefaultMessage)
            .orElse("Validation failed");
    return fail(ApiErrorCode.VALIDATION_ERROR, message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    String message =
        ex.getConstraintViolations().stream()
            .findFirst()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .orElse("Validation failed");
    return fail(ApiErrorCode.VALIDATION_ERROR, message);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleHeader(MissingRequestHeaderException ex) {
    return fail(ApiErrorCode.MISSING_HEADER, ex.getHeaderName() + " is required");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleReadable(Exception ex) {
    return fail(ApiErrorCode.MALFORMED_REQUEST, "Request body or parameter is invalid");
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
    return fail(ApiErrorCode.UNAUTHORIZED, "Authentication required");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
    return fail(ApiErrorCode.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ApiResponse<Void>> handleMissing(Exception ex) {
    return fail(ApiErrorCode.NOT_FOUND, "Not found");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleStale(OptimisticLockingFailureException ex) {
    return fail(ApiErrorCode.CONCURRENT_UPDATE, "This record was changed by another request");
  }

  @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
  public ResponseEntity<ApiResponse<Void>> handleDb(Exception ex) {
    log.warn("dependency failure", ex);
    return fail(ApiErrorCode.RETRYABLE, "A dependency is unavailable");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
    log.error("unhandled", ex);
    return fail(ApiErrorCode.INTERNAL_ERROR, "Unexpected error");
  }

  private static ResponseEntity<ApiResponse<Void>> fail(ApiErrorCode error, String message) {
    return ResponseEntity.status(error.status()).body(ApiResponse.fail(error, message));
  }
}
