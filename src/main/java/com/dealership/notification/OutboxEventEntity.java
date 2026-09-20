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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "event_type", nullable = false, columnDefinition = "outbox_event_type")
  private OutboxEventType eventType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private MailSnapshot payload;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "outbox_status")
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 1024)
  private String lastError;

  @Column(name = "locked_by", length = 64)
  private String lockedBy;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "published_at")
  private Instant publishedAt;

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

  public void markPublished() {
    status = OutboxStatus.PUBLISHED;
    publishedAt = Instant.now();
    lockedBy = null;
    leaseExpiresAt = null;
  }

  public UUID getId() {
    return id;
  }

  public MailSnapshot getPayload() {
    return payload;
  }

  public void setEventType(OutboxEventType eventType) {
    this.eventType = eventType;
  }

  public void setAggregateId(UUID aggregateId) {
    this.aggregateId = aggregateId;
  }

  public void setPayload(MailSnapshot payload) {
    this.payload = payload;
  }

  public void setStatus(OutboxStatus status) {
    this.status = status;
  }
}
