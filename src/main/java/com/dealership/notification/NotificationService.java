package com.dealership.notification;

import com.dealership.reminder.ReminderRepository;
import com.dealership.reminder.ReminderRepository.MailFacts;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notifications;
  private final OutboxEventRepository outboxEvents;
  private final ReminderRepository reminders;

  public NotificationService(
      NotificationRepository notifications,
      OutboxEventRepository outboxEvents,
      ReminderRepository reminders) {
    this.notifications = notifications;
    this.outboxEvents = outboxEvents;
    this.reminders = reminders;
  }

  @Transactional
  public void enqueueDue(MailFacts facts) {
    MailSnapshot snapshot =
        new MailSnapshot(
            facts.reminderId(),
            facts.appointmentId(),
            facts.offsetMinutes(),
            facts.scheduleVersion(),
            facts.scheduledAt(),
            facts.displayOffset(),
            facts.dealershipName(),
            facts.customerName(),
            facts.vehicleMake(),
            facts.vehicleModel(),
            facts.vehicleYear(),
            facts.registrationNumber(),
            facts.contact(),
            facts.idempotencyKey(),
            facts.notifyEnabled(),
            facts.attempts());
    OutboxEventEntity event = new OutboxEventEntity();
    event.setEventType(OutboxEventType.REMINDER_DUE);
    event.setAggregateId(facts.reminderId());
    event.setPayload(snapshot);
    event.setStatus(OutboxStatus.PENDING);
    outboxEvents.save(event);
    ensureNotification(facts);
  }

  @Transactional
  public void replay(UUID notificationId) {
    NotificationEntity row =
        notifications.findById(notificationId).orElseThrow(ApiException::notFound);
    if (row.getStatus() == NotificationStatus.SENT) {
      throw ApiException.of(ApiErrorCode.ALREADY_SENT, "Notification already sent");
    }
    var facts = reminders.loadMailFacts(row.getReminderId()).orElseThrow(ApiException::notFound);
    enqueueDue(facts);
  }

  @Transactional(readOnly = true)
  public boolean alreadySent(String idempotencyKey) {
    return notifications.existsByIdempotencyKeyAndStatus(idempotencyKey, NotificationStatus.SENT);
  }

  @Transactional
  public void markSent(String idempotencyKey) {
    notifications.findByIdempotencyKey(idempotencyKey).ifPresent(NotificationEntity::markSent);
  }

  @Transactional
  public void markRetry(String idempotencyKey, Instant next, String error) {
    notifications.findByIdempotencyKey(idempotencyKey).ifPresent(row -> row.markRetry(next, error));
  }

  @Transactional
  public void markDead(String idempotencyKey, String error) {
    notifications.findByIdempotencyKey(idempotencyKey).ifPresent(row -> row.markDead(error));
  }

  @Transactional
  public void markPublished(UUID outboxId) {
    outboxEvents.findById(outboxId).ifPresent(OutboxEventEntity::markPublished);
  }

  private void ensureNotification(MailFacts facts) {
    if (notifications.existsByIdempotencyKey(facts.idempotencyKey())) {
      return;
    }
    NotificationEntity row = new NotificationEntity();
    row.setReminderId(facts.reminderId());
    row.setAppointmentId(facts.appointmentId());
    row.setOffsetMinutes(facts.offsetMinutes());
    row.setIdempotencyKey(facts.idempotencyKey());
    row.setStatus(NotificationStatus.PENDING);
    try {
      notifications.saveAndFlush(row);
    } catch (DataIntegrityViolationException ignored) {
      // unique idempotency_key: concurrent claim
    }
  }
}
