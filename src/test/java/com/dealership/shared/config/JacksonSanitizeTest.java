package com.dealership.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dealership.notification.MailSnapshot;
import com.dealership.notification.NotificationDtos;
import com.dealership.shared.config.JacksonConfig.SanitizedStringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class JacksonSanitizeTest {

  @Test
  void jsonBodyKeepsNewlinesSubjectStripsThem() throws Exception {
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(
                new SimpleModule()
                    .addDeserializer(String.class, new SanitizedStringDeserializer()));

    NotificationDtos.ManualSendRequest request =
        mapper.readValue(
            """
            {"subject":"Hi\\nthere","body":"Hi Dheeraj,\\n\\nThanks"}
            """,
            NotificationDtos.ManualSendRequest.class);
    assertEquals("Hithere", request.subject());
    assertEquals("Hi Dheeraj,\n\nThanks", request.body());

    MailSnapshot snapshot =
        mapper.readValue(
            """
            {
              "reminderId": null,
              "notificationId": "00000000-0000-0000-0000-000000000001",
              "appointmentId": "00000000-0000-0000-0000-000000000002",
              "dealershipId": "00000000-0000-0000-0000-000000000003",
              "offsetMinutes": null,
              "scheduleVersion": null,
              "scheduledAt": "2026-09-22T16:30:00Z",
              "displayOffset": "+05:30",
              "dealershipName": "North Shop",
              "customerName": "Dheeraj",
              "vehicleMake": "Honda",
              "vehicleModel": "Civic",
              "vehicleYear": 2022,
              "registrationNumber": "KA01AB1234",
              "contact": "customer@example.com",
              "idempotencyKey": "key",
              "notifyEnabled": true,
              "attempts": 0,
              "generation": "MANUAL",
              "subject": "Hi\\nX",
              "body": "Hi Dheeraj,\\n\\nThank you"
            }
            """,
            MailSnapshot.class);
    assertEquals("HiX", snapshot.subject());
    assertEquals("Hi Dheeraj,\n\nThank you", snapshot.body());
  }
}
