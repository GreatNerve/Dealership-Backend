package com.dealership.shared.config;

import com.dealership.shared.api.Inputs;
import com.dealership.shared.api.MultilineStringDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
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

  static final class SanitizedStringDeserializer extends JsonDeserializer<String>
      implements ContextualDeserializer {
    private static final SanitizedStringDeserializer STRIP = new SanitizedStringDeserializer(false);
    private static final SanitizedStringDeserializer KEEP = new SanitizedStringDeserializer(true);

    private final boolean keepNewlines;

    SanitizedStringDeserializer() {
      this(false);
    }

    private SanitizedStringDeserializer(boolean keepNewlines) {
      this.keepNewlines = keepNewlines;
    }

    @Override
    public JsonDeserializer<?> createContextual(
        DeserializationContext context, BeanProperty property) {
      // Type-level String deserializer wins over @JsonDeserialize on records;
      // outbox/Rabbit also round-trip MailSnapshot.body through this mapper.
      if (property != null && keepNewlines(property)) {
        return KEEP;
      }
      return STRIP;
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (parser.hasToken(JsonToken.VALUE_NULL)) {
        return null;
      }
      String raw = parser.getValueAsString();
      return keepNewlines ? Inputs.multiline(raw) : Inputs.sanitize(raw);
    }

    private static boolean keepNewlines(BeanProperty property) {
      if ("body".equals(property.getName())) {
        return true;
      }
      JsonDeserialize json = property.getAnnotation(JsonDeserialize.class);
      return json != null && json.using() == MultilineStringDeserializer.class;
    }
  }
}
