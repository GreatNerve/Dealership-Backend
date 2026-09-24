package com.dealership.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "notification_delivery_events")
public class NotificationDeliveryEventEntity {

  @Id private UUID id;

  @Column(name = "notification_id", nullable = false)
  private UUID notificationId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "event_type", nullable = false, columnDefinition = "delivery_event_type")
  private DeliveryEventType eventType;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "delivery_provider")
  private DeliveryProvider provider;

  @Column(name = "provider_event_id", nullable = false, length = 255)
  private String providerEventId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "raw_type", length = 64)
  private String rawType;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(UUID notificationId) {
    this.notificationId = notificationId;
  }

  public DeliveryEventType getEventType() {
    return eventType;
  }

  public void setEventType(DeliveryEventType eventType) {
    this.eventType = eventType;
  }

  public DeliveryProvider getProvider() {
    return provider;
  }

  public void setProvider(DeliveryProvider provider) {
    this.provider = provider;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public void setProviderEventId(String providerEventId) {
    this.providerEventId = providerEventId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public String getRawType() {
    return rawType;
  }

  public void setRawType(String rawType) {
    this.rawType = rawType;
  }
}
