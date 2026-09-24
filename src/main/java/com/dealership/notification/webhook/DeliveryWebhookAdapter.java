package com.dealership.notification.webhook;

import com.dealership.notification.DeliveryEventType;
import com.dealership.notification.DeliveryProvider;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryWebhookAdapter {

  /** One HTTP / one transaction; a valid secret must not hold the pool on a huge array. */
  int MAX_BATCH = 100;

  String provider();

  DeliveryProvider ledgerProvider();

  Optional<Ingest> parse(JsonNode body);

  default List<Ingest> parseAll(JsonNode body) {
    if (body == null || body.isNull()) {
      return List.of();
    }
    if (body.isArray()) {
      if (body.size() > MAX_BATCH) {
        throw ApiException.of(
            ApiErrorCode.VALIDATION_ERROR, "delivery event batch larger than " + MAX_BATCH);
      }
      List<Ingest> out = new ArrayList<>();
      for (JsonNode node : body) {
        parse(node).ifPresent(out::add);
      }
      return out;
    }
    return parse(body).map(List::of).orElseGet(List::of);
  }

  record Ingest(
      UUID notificationId,
      DeliveryEventType type,
      String providerEventId,
      Instant occurredAt,
      String rawType) {}
}
