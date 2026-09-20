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
@Table(name = "notifications")
public class NotificationEntity {

  @Id private UUID id;

  @Column(name = "reminder_id", nullable = false)
  private UUID reminderId;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "offset_minutes", nullable = false)
  private int offsetMinutes;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "notification_status")
  private NotificationStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 1024)
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

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

  public void markSent() {
    status = NotificationStatus.SENT;
    sentAt = Instant.now();
  }

  public void markRetry(Instant next, String error) {
    status = NotificationStatus.RETRY_SCHEDULED;
    attempts = attempts + 1;
    nextAttemptAt = next;
    lastError = truncate(error);
  }

  public void markDead(String error) {
    status = NotificationStatus.DEAD_LETTER;
    attempts = attempts + 1;
    lastError = truncate(error);
  }

  private static String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= 1024 ? error : error.substring(0, 1024);
  }

  public UUID getId() {
    return id;
  }

  public UUID getReminderId() {
    return reminderId;
  }

  public void setReminderId(UUID reminderId) {
    this.reminderId = reminderId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public void setOffsetMinutes(int offsetMinutes) {
    this.offsetMinutes = offsetMinutes;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }
}
