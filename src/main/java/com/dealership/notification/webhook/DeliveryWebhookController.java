package com.dealership.notification.webhook;

import com.dealership.notification.NotificationService;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.security.Secrets;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/delivery")
@Tag(name = "Notification")
public class DeliveryWebhookController {

  private static final Logger log = LoggerFactory.getLogger(DeliveryWebhookController.class);

  private final Map<String, DeliveryWebhookAdapter> adapters;
  private final NotificationService notifications;
  private final AppProperties properties;

  public DeliveryWebhookController(
      List<DeliveryWebhookAdapter> adapters,
      NotificationService notifications,
      AppProperties properties) {
    this.adapters =
        adapters.stream()
            .collect(
                Collectors.toMap(a -> a.provider().toLowerCase(Locale.ROOT), Function.identity()));
    this.notifications = notifications;
    this.properties = properties;
  }

  @PostMapping("/{provider}")
  @SecurityRequirements
  @Operation(summary = "Ingest a provider Delivery Event")
  public ResponseEntity<Void> ingest(
      @PathVariable String provider,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody(required = false) JsonNode body) {
    String who = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
    if (!secretOk(authorization)) {
      log.info(
          "delivery webhook unauthorized provider={} hasAuthorization={}",
          who,
          authorization != null && !authorization.isBlank());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    DeliveryWebhookAdapter adapter = adapters.get(who);
    if (adapter == null) {
      log.info("delivery webhook unknown provider={}", who);
      return ResponseEntity.notFound().build();
    }
    var parsed = adapter.parseAll(body);
    if (parsed.isEmpty()) {
      log.info(
          "delivery webhook empty provider={} event={} hasCorrelation={} fields={}",
          who,
          eventName(body),
          hasCorrelation(body),
          fieldNames(body));
      return ResponseEntity.noContent().build();
    }
    if (!notifications.ingestEvents(adapter.ledgerProvider(), parsed)) {
      log.info("delivery webhook unknown notification provider={} count={}", who, parsed.size());
      return ResponseEntity.noContent().build();
    }
    log.info("delivery events provider={} count={}", who, parsed.size());
    return ResponseEntity.ok().build();
  }

  private boolean secretOk(String authorization) {
    String expected = properties.getNotifications().getWebhookSecret();
    if (expected == null || expected.isBlank()) {
      return false;
    }
    return Secrets.equal(expected, Secrets.presented(authorization));
  }

  private static String eventName(JsonNode body) {
    if (body == null || body.isNull()) {
      return "";
    }
    if (body.isArray()) {
      return "array:" + body.size();
    }
    return body.path("event").asText("");
  }

  private static boolean hasCorrelation(JsonNode body) {
    if (body == null || !body.isObject()) {
      return false;
    }
    String raw = body.path("X-Mailin-custom").asText("");
    if (raw.isBlank()) {
      return false;
    }
    try {
      UUID.fromString(raw);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static String fieldNames(JsonNode body) {
    if (body == null || body.isNull()) {
      return "null";
    }
    if (body.isArray()) {
      return "array";
    }
    if (!body.isObject()) {
      return body.getNodeType().name();
    }
    List<String> names = new ArrayList<>();
    body.fieldNames().forEachRemaining(names::add);
    return String.join(",", names);
  }
}
