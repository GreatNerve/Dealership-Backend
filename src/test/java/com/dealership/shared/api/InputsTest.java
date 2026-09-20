package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class InputsTest {

  @Test
  void sanitizeTrimsAndStripsControls() {
    assertNull(Inputs.sanitize(null));
    assertEquals("hello", Inputs.sanitize("  hel\u0000lo \n"));
    assertEquals("", Inputs.sanitize(" \t\u0000 "));
  }

  @Test
  void emailLowercasesAfterSanitize() {
    assertEquals("a@ex.com", Inputs.email("  A@Ex.COM\u200B "));
  }

  @Test
  void pageQuerySanitizesQAndTreatsBlankAsAbsent() {
    assertEquals("Civic", PageQuery.bind(0, 10, "  \u0000Civic  ", 100, 1000, 100).q());
    assertNull(PageQuery.bind(0, 10, " \u0000 ", 100, 1000, 100).q());
  }
}
