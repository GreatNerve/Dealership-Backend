package com.dealership.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorWriter {

  private final ObjectMapper mapper;

  public ApiErrorWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletResponse response, ApiErrorCode error, String message)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(error.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), ApiResponse.fail(error, message));
  }
}
