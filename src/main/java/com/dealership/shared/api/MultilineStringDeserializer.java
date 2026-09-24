package com.dealership.shared.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public final class MultilineStringDeserializer extends JsonDeserializer<String> {

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.hasToken(JsonToken.VALUE_NULL)) {
      return null;
    }
    return Inputs.multiline(parser.getValueAsString());
  }
}
