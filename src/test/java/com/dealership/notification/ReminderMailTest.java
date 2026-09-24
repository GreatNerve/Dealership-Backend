package com.dealership.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReminderMailTest {

  @Test
  void bodyUsesLocalBookingTimeAndVehicle() {
    ReminderMail mail = ReminderMail.of(snapshot(1440, "North Shop"));
    assertTrue(mail.text().contains("Tuesday, 22 September 2026"));
    assertTrue(mail.text().contains("10:00 PM"));
    assertFalse(mail.text().toUpperCase().contains("UTC"));
    assertFalse(mail.html().toUpperCase().contains("UTC"));
    assertFalse(mail.text().contains("16:30"));
    assertTrue(mail.text().contains("2022 Honda Civic · KA01AB1234"));
    assertTrue(mail.html().contains("10:00 PM"));
    assertFalse(mail.html().contains("Your visit at"));
    assertTrue(mail.html().contains("2022 Honda Civic · KA01AB1234"));
    assertEquals("Service appointment — North Shop", mail.subject());
    assertFalse(mail.text().contains("2-hour"));
    assertFalse(mail.subject().contains("24-hour"));
    assertFalse(mail.html().contains("2-hour reminder"));
    assertFalse(mail.text().startsWith("Hi "));
  }

  @Test
  void greetingWhenNamePresent() {
    ReminderMail mail = ReminderMail.of(snapshot(1440, "North Shop", "Dheeraj"));
    assertTrue(mail.text().startsWith("Hi Dheeraj,"));
    assertTrue(mail.html().contains("Hi Dheeraj,"));
  }

  @Test
  void htmlEscapesDealershipName() {
    ReminderMail mail = ReminderMail.of(snapshot(120, "A <script>x</script> Shop"));
    assertTrue(mail.html().contains("A &lt;script&gt;x&lt;/script&gt; Shop"));
    assertFalse(mail.html().contains("<script>x</script>"));
  }

  @Test
  void staffMailUsesSameHtmlShellAndKeepsParagraphs() {
    MailSnapshot snapshot =
        new MailSnapshot(
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            Instant.parse("2026-09-22T16:30:00Z"),
            "+05:30",
            "North Shop",
            "Dheeraj",
            "Honda",
            "Civic",
            2022,
            "KA01AB1234",
            "customer@example.com",
            "key",
            true,
            0,
            NotificationGeneration.MANUAL,
            "We hope to see you again at North Shop",
            "Hi Dheeraj,\n\nThank you for bringing KA01AB1234 in.\n\nNorth Shop");
    ReminderMail mail = ReminderMail.of(snapshot);
    assertEquals("We hope to see you again at North Shop", mail.subject());
    assertTrue(mail.text().contains("Hi Dheeraj,\n\nThank you"));
    assertTrue(mail.html().contains("#efe8dc"));
    assertTrue(mail.html().contains("Hi Dheeraj,"));
    assertTrue(mail.html().contains("Thank you for bringing KA01AB1234 in."));
    assertTrue(mail.html().contains("<br>"));
    assertFalse(mail.html().contains("Hi Dheeraj,Thank you"));
    assertFalse(mail.html().contains("<script>"));
  }

  @Test
  void vehicleLine() {
    assertEquals(
        "2022 Honda Civic · KA01AB1234", ReminderMail.vehicleLine(snapshot(120, "North Shop")));
  }

  private static MailSnapshot snapshot(int offsetMinutes, String shop) {
    return snapshot(offsetMinutes, shop, null);
  }

  private static MailSnapshot snapshot(int offsetMinutes, String shop, String customerName) {
    return new MailSnapshot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        offsetMinutes,
        1,
        Instant.parse("2026-09-22T16:30:00Z"),
        "+05:30",
        shop,
        customerName,
        "Honda",
        "Civic",
        2022,
        "KA01AB1234",
        "customer@example.com",
        "key",
        true,
        0,
        NotificationGeneration.SYSTEM,
        null,
        null);
  }
}
