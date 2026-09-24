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

  @Column(name = "dealership_id", nullable = false)
  private UUID dealershipId;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "reminder_id")
  private UUID reminderId;

  @Column(name = "offset_minutes")
  private Integer offsetMinutes;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "notification_channel")
  private NotificationChannel channel = NotificationChannel.EMAIL;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "notification_generation")
  private NotificationGeneration generation = NotificationGeneration.SYSTEM;

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

  @Column(name = "locked_by", length = 64)
  private String lockedBy;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(length = 255)
  private String subject;

  @Column(columnDefinition = "text")
  private String body;

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

  public void markReplay() {
    status = NotificationStatus.PENDING;
    attempts = 0;
    lastError = null;
    nextAttemptAt = null;
    sentAt = null;
    lockedBy = null;
    leaseExpiresAt = null;
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

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getDealershipId() {
    return dealershipId;
  }

  public void setDealershipId(UUID dealershipId) {
    this.dealershipId = dealershipId;
  }

  public UUID getReminderId() {
    return reminderId;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setReminderId(UUID reminderId) {
    this.reminderId = reminderId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public Integer getOffsetMinutes() {
    return offsetMinutes;
  }

  public void setOffsetMinutes(Integer offsetMinutes) {
    this.offsetMinutes = offsetMinutes;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public void setChannel(NotificationChannel channel) {
    this.channel = channel;
  }

  public NotificationGeneration getGeneration() {
    return generation;
  }

  public void setGeneration(NotificationGeneration generation) {
    this.generation = generation;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
