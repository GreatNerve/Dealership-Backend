package com.dealership.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dealership.notification.smtp.MailWorker;
import com.dealership.notification.smtp.NotificationSender;
import com.dealership.notification.webhook.BrevoDeliveryWebhookAdapter;
import com.dealership.notification.webhook.DeliveryWebhookAdapter;
import com.dealership.reminder.ReminderRepository;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.metrics.AppMetrics;
import com.dealership.shared.time.TimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderAcceptHealTest {

  @Mock NotificationRepository notifications;
  @Mock NotificationDeliveryEventRepository events;
  @Mock ReminderRepository reminders;
  @Mock AppMetrics metrics;
  @Mock NotificationSender sender;
  @Mock com.dealership.notification.FileNotificationLog fileLog;
  @Mock AppProperties properties;
  @Mock TimeProvider time;

  private NotificationService service;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    service =
        new NotificationService(
            notifications,
            events,
            null,
            null,
            reminders,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            metrics);
  }

  @Test
  void brevoRequestHealsOpenNotificationWithoutAnotherSend() throws Exception {
    UUID notificationId = UUID.randomUUID();
    UUID reminderId = UUID.randomUUID();
    NotificationEntity row = open(notificationId, reminderId);
    when(notifications.findAllById(any())).thenReturn(List.of(row));
    when(events.findByNotificationIdIn(any())).thenReturn(List.of());

    DeliveryWebhookAdapter.Ingest ingest =
        new BrevoDeliveryWebhookAdapter()
            .parse(
                mapper.readTree(
                    """
{"event":"request","X-Mailin-custom":"%s","message-id":"m1","ts_epoch":1700000000}
"""
                        .formatted(notificationId)))
            .orElseThrow();

    service.ingestEvents(DeliveryProvider.BREVO, List.of(ingest));

    assertEquals(NotificationStatus.SENT, row.getStatus());
    verify(reminders).markSentFromProvider(List.of(reminderId));
    verify(events).saveAll(any());
  }

  @Test
  void openedDoesNotHealWorkerStatus() throws Exception {
    UUID notificationId = UUID.randomUUID();
    NotificationEntity row = open(notificationId, UUID.randomUUID());
    when(notifications.findAllById(any())).thenReturn(List.of(row));
    when(events.findByNotificationIdIn(any())).thenReturn(List.of());

    DeliveryWebhookAdapter.Ingest ingest =
        new BrevoDeliveryWebhookAdapter()
            .parse(
                mapper.readTree(
                    """
{"event":"unique_opened","X-Mailin-custom":"%s","message-id":"m1","ts_epoch":1700000000}
"""
                        .formatted(notificationId)))
            .orElseThrow();

    service.ingestEvents(DeliveryProvider.BREVO, List.of(ingest));

    assertEquals(NotificationStatus.PENDING, row.getStatus());
    verify(reminders, never()).markSentFromProvider(any());
  }

  @Test
  void mailWorkerSkipsSmtpWhenProviderAlreadyAccepted() throws Exception {
    UUID notificationId = UUID.randomUUID();
    UUID reminderId = UUID.randomUUID();
    String key = UUID.randomUUID() + ":1440:1";
    NotificationEntity row = open(notificationId, reminderId);
    when(notifications.existsByIdempotencyKeyAndStatus(key, NotificationStatus.SENT))
        .thenReturn(false);
    when(reminders.heartbeat(any(), any(), any())).thenReturn(true);
    when(events.existsByNotificationIdAndEventTypeIn(eq(notificationId), any())).thenReturn(true);
    when(notifications.findByIdempotencyKey(key)).thenReturn(Optional.of(row));
    when(properties.getWorkers()).thenReturn(new AppProperties.Workers());

    MailWorker worker =
        new MailWorker(sender, fileLog, reminders, service, properties, time, metrics);
    worker.onMessage(snapshot(notificationId, reminderId, key));

    verify(sender, never()).send(any(), any(), any());
    verify(reminders).markSent(eq(reminderId), any());
    assertEquals(NotificationStatus.SENT, row.getStatus());
  }

  private static NotificationEntity open(UUID notificationId, UUID reminderId) {
    NotificationEntity row = new NotificationEntity();
    row.setId(notificationId);
    row.setReminderId(reminderId);
    row.setStatus(NotificationStatus.PENDING);
    return row;
  }

  private static MailSnapshot snapshot(UUID notificationId, UUID reminderId, String key) {
    return new MailSnapshot(
        reminderId,
        notificationId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        1440,
        1,
        Instant.parse("2026-09-28T16:54:00Z"),
        "+05:30",
        "Shop",
        "Ada",
        "Tata",
        "Nexon",
        2024,
        "GJ01AB1234",
        "ada@ex.com",
        key,
        true,
        0,
        NotificationGeneration.SYSTEM,
        null,
        null);
  }
}
