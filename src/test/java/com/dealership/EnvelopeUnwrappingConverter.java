package com.dealership;

import com.dealership.shared.api.ApiResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Type;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

final class EnvelopeUnwrappingConverter extends MappingJackson2HttpMessageConverter {

  EnvelopeUnwrappingConverter(ObjectMapper mapper) {
    super(mapper);
  }

  @Override
  public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    return unwrap(getJavaType(type, contextClass), inputMessage);
  }

  @Override
  protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    return unwrap(getObjectMapper().constructType(clazz), inputMessage);
  }

  private Object unwrap(JavaType target, HttpInputMessage inputMessage) throws IOException {
    JsonNode root = getObjectMapper().readTree(inputMessage.getBody());
    if (root != null
        && root.path("success").isBoolean()
        && !ApiResponse.class.isAssignableFrom(target.getRawClass())) {
      JsonNode data = root.get("data");
      if (data == null || data.isNull()) {
        return null;
      }
      return getObjectMapper().convertValue(data, target);
    }
    return getObjectMapper().convertValue(root, target);
  }
}
