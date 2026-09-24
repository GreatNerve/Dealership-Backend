package com.dealership.notification.webhook;

import com.dealership.notification.DeliveryEventType;
import com.dealership.notification.DeliveryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BrevoDeliveryWebhookAdapter implements DeliveryWebhookAdapter {

  @Override
  public String provider() {
    return "brevo";
  }

  @Override
  public DeliveryProvider ledgerProvider() {
    return DeliveryProvider.BREVO;
  }

  @Override
  public Optional<Ingest> parse(JsonNode body) {
    if (body == null || body.isNull()) {
      return Optional.empty();
    }
    UUID id = uuid(body.path("X-Mailin-custom").asText(null));
    if (id == null) {
      return Optional.empty();
    }
    String raw = body.path("event").asText("");
    DeliveryEventType type = map(raw);
    if (type == null) {
      return Optional.empty();
    }
    String messageId = body.path("message-id").asText("");
    long ts = body.path("ts_epoch").asLong(0);
    String providerEventId = messageId + ":" + raw + ":" + ts;
    return Optional.of(new Ingest(id, type, providerEventId, occurredAt(ts), raw));
  }

  // Brevo ts_epoch is milliseconds; ts / tests may send seconds. Seconds stay < 1e12 until year
  // 33658.
  static Instant occurredAt(long ts) {
    if (ts <= 0) {
      return Instant.now();
    }
    return ts >= 1_000_000_000_000L ? Instant.ofEpochMilli(ts) : Instant.ofEpochSecond(ts);
  }

  private static DeliveryEventType map(String raw) {
    return switch (raw == null ? "" : raw.toLowerCase(Locale.ROOT)) {
      case "request", "sent" -> DeliveryEventType.ACCEPTED;
      case "delivered" -> DeliveryEventType.DELIVERED;
      case "soft_bounce" -> DeliveryEventType.SOFT_BOUNCE;
      case "hard_bounce" -> DeliveryEventType.HARD_BOUNCE;
      case "unique_opened", "opened", "first_opening" -> DeliveryEventType.OPENED;
      case "click", "clicked" -> DeliveryEventType.CLICKED;
      case "spam" -> DeliveryEventType.SPAM;
      case "blocked" -> DeliveryEventType.BLOCKED;
      case "error", "invalid_email" -> DeliveryEventType.ERROR;
      default -> null;
    };
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
