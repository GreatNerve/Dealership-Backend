package com.dealership.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderOffsetsTest {

  @Test
  void defaultOffsetsAreTwentyFourAndTwoHours() {
    assertEquals(List.of(1440, 120), new AppProperties.Reminders().offsetMinutes());
  }

  @Test
  void extraOffsetsExpandWithoutATypeEnum() {
    var reminders = new AppProperties.Reminders();
    reminders.setOffsets(
        List.of(
            Duration.ofDays(7), Duration.ofHours(24), Duration.ofHours(6), Duration.ofHours(2)));
    assertEquals(List.of(10080, 1440, 360, 120), reminders.offsetMinutes());
  }

  @Test
  void duplicateOffsetsAreRejected() {
    var reminders = new AppProperties.Reminders();
    reminders.setOffsets(List.of(Duration.ofHours(6), Duration.ofHours(6)));
    assertThrows(IllegalStateException.class, reminders::offsetMinutes);
  }
}
