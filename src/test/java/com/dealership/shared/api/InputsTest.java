package com.dealership.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class InputsTest {

  @Test
  void clipAndFitKeepShortAndHashLong() {
    assertNull(Inputs.clip(null, 8));
    assertEquals("hello", Inputs.clip("hello", 8));
    assertEquals("hello", Inputs.clip("hello!!!", 5));
    assertEquals("", Inputs.fit(null, 255));
    assertEquals("e1", Inputs.fit("e1", 255));
    String longId = "m".repeat(300);
    String hashed = Inputs.fit(longId, 255);
    assertEquals(64, hashed.length());
    assertEquals(hashed, Inputs.fit(longId, 255));
    assertNotEquals(hashed, Inputs.fit("n".repeat(300), 255));
  }

  @Test
  void sanitizeDropsNewlinesMultilineKeepsThem() {
    assertEquals("Hi Dheeraj,Thanks", Inputs.sanitize("Hi Dheeraj,\n\nThanks"));
    assertEquals("Hi Dheeraj,\n\nThanks", Inputs.multiline("Hi Dheeraj,\r\n\r\nThanks"));
    assertEquals("Thanks", Inputs.multiline("\u0000Thanks\u0000"));
  }
}
