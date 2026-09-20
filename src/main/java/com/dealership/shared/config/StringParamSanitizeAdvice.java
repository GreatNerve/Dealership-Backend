package com.dealership.shared.config;

import com.dealership.shared.api.Inputs;
import java.beans.PropertyEditorSupport;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
public class StringParamSanitizeAdvice {

  @InitBinder
  public void bindSanitizedStrings(WebDataBinder binder) {
    // Query, form, and header strings skip the JSON deserializer.
    binder.registerCustomEditor(
        String.class,
        new PropertyEditorSupport() {
          @Override
          public void setAsText(String text) {
            setValue(Inputs.sanitize(text));
          }
        });
  }
}
