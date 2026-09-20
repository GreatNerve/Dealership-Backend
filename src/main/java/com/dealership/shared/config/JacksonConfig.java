package com.dealership.shared.config;

import com.dealership.shared.api.Inputs;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer sanitizedStrings() {
    // JSON strings skip @InitBinder; Inputs is the one strip/trim.
    return builder -> builder.deserializerByType(String.class, new SanitizedStringDeserializer());
  }

  static final class SanitizedStringDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (parser.hasToken(JsonToken.VALUE_NULL)) {
        return null;
      }
      return Inputs.sanitize(parser.getValueAsString());
    }
  }
}
