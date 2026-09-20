package com.dealership.shared.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    String path = request.getURI().getPath();
    if (!path.contains("/api/v1") || body instanceof ApiResponse<?>) {
      return body;
    }
    // Swagger OAuth2 password flow needs top-level access_token, not the envelope.
    MediaType contentType = request.getHeaders().getContentType();
    if (path.endsWith("/auth/login")
        && contentType != null
        && contentType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
      return body;
    }
    return ApiResponse.ok(body);
  }
}
