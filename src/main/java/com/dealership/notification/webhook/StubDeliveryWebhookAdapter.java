package com.dealership.notification.webhook;

import com.dealership.notification.DeliveryEventType;
import com.dealership.notification.DeliveryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubDeliveryWebhookAdapter implements DeliveryWebhookAdapter {

  @Override
  public String provider() {
    return "stub";
  }

  @Override
  public DeliveryProvider ledgerProvider() {
    return DeliveryProvider.STUB;
  }

  @Override
  public Optional<Ingest> parse(JsonNode body) {
    if (body == null || body.isNull()) {
      return Optional.empty();
    }
    UUID id = uuid(body.path("correlationKey").asText(null));
    if (id == null) {
      return Optional.empty();
    }
    String raw = body.path("event").asText("OTHER");
    String providerEventId = body.path("providerEventId").asText(id + ":" + raw);
    Instant at =
        body.hasNonNull("occurredAt")
            ? Instant.parse(body.get("occurredAt").asText())
            : Instant.now();
    return Optional.of(new Ingest(id, map(raw), providerEventId, at, raw));
  }

  private static DeliveryEventType map(String raw) {
    try {
      return DeliveryEventType.valueOf(raw);
    } catch (RuntimeException ignored) {
      return DeliveryEventType.OTHER;
    }
  }

  private static UUID uuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
