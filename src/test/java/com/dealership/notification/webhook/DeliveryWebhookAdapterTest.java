package com.dealership.notification.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.notification.DeliveryEventType;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryWebhookAdapterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void brevoMapsCustomHeaderAndOpened() throws Exception {
    UUID id = UUID.randomUUID();
    var node =
        mapper.readTree(
            """
            {"event":"unique_opened","X-Mailin-custom":"%s","message-id":"m1","ts_epoch":1700000000}
            """
                .formatted(id));
    var ingest = new BrevoDeliveryWebhookAdapter().parse(node).orElseThrow();
    assertEquals(id, ingest.notificationId());
    assertEquals(DeliveryEventType.OPENED, ingest.type());
    assertEquals(Instant.ofEpochSecond(1_700_000_000L), ingest.occurredAt());
  }

  @Test
  void brevoTsEpochMillisecondsNotSeconds() throws Exception {
    UUID id = UUID.randomUUID();
    var node =
        mapper.readTree(
            """
            {"event":"blocked","X-Mailin-custom":"%s","message-id":"m1","ts_epoch":1758660000000}
            """
                .formatted(id));
    var ingest = new BrevoDeliveryWebhookAdapter().parse(node).orElseThrow();
    assertEquals(DeliveryEventType.BLOCKED, ingest.type());
    assertEquals(Instant.ofEpochMilli(1_758_660_000_000L), ingest.occurredAt());
  }

  @Test
  void brevoUnknownEventIsEmpty() throws Exception {
    UUID id = UUID.randomUUID();
    var node =
        mapper.readTree(
            """
            {"event":"deferred","X-Mailin-custom":"%s","message-id":"m1","ts_epoch":1}
            """
                .formatted(id));
    assertTrue(new BrevoDeliveryWebhookAdapter().parse(node).isEmpty());
  }

  @Test
  void brevoArrayIngestsEachObject() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var node =
        mapper.readTree(
            """
            [
              {"event":"delivered","X-Mailin-custom":"%s","message-id":"m1","ts_epoch":1},
              {"event":"unique_opened","X-Mailin-custom":"%s","message-id":"m2","ts_epoch":2}
            ]
            """
                .formatted(first, second));
    var parsed = new BrevoDeliveryWebhookAdapter().parseAll(node);
    assertEquals(2, parsed.size());
    assertEquals(first, parsed.get(0).notificationId());
    assertEquals(DeliveryEventType.DELIVERED, parsed.get(0).type());
    assertEquals(second, parsed.get(1).notificationId());
    assertEquals(DeliveryEventType.OPENED, parsed.get(1).type());
  }

  @Test
  void brevoArrayLargerThanMaxIsInvalid() throws Exception {
    ArrayNode arr = mapper.createArrayNode();
    for (int i = 0; i < DeliveryWebhookAdapter.MAX_BATCH + 1; i++) {
      arr.add(
          mapper.readTree(
              """
              {"event":"delivered","X-Mailin-custom":"%s","message-id":"m","ts_epoch":1}
              """
                  .formatted(UUID.randomUUID())));
    }
    ApiException ex =
        assertThrows(ApiException.class, () -> new BrevoDeliveryWebhookAdapter().parseAll(arr));
    assertEquals(ApiErrorCode.VALIDATION_ERROR, ex.error());
  }

  @Test
  void stubUsesCorrelationKey() throws Exception {
    UUID id = UUID.randomUUID();
    var node =
        mapper.readTree(
            """
            {"event":"OPENED","correlationKey":"%s","providerEventId":"e1"}
            """
                .formatted(id));
    var ingest = new StubDeliveryWebhookAdapter().parse(node).orElseThrow();
    assertEquals(id, ingest.notificationId());
    assertEquals(DeliveryEventType.OPENED, ingest.type());
    assertEquals("e1", ingest.providerEventId());
  }
}
