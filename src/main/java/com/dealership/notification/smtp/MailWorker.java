package com.dealership.notification.smtp;

import com.dealership.notification.FileNotificationLog;
import com.dealership.notification.MailSnapshot;
import com.dealership.notification.NotificationService;
import com.dealership.reminder.ReminderRepository;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.config.RabbitConfig;
import com.dealership.shared.metrics.AppMetrics;
import com.dealership.shared.time.TimeProvider;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MailWorker {

  private static final Logger log = LoggerFactory.getLogger(MailWorker.class);

  private final NotificationSender sender;
  private final FileNotificationLog fileLog;
  private final ReminderRepository reminders;
  private final NotificationService notifications;
  private final AppProperties properties;
  private final TimeProvider time;
  private final AppMetrics metrics;
  private final String workerId = ReminderRepository.MAIL_WORKER_PREFIX + UUID.randomUUID();
  private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

  public MailWorker(
      NotificationSender sender,
      FileNotificationLog fileLog,
      ReminderRepository reminders,
      NotificationService notifications,
      AppProperties properties,
      TimeProvider time,
      AppMetrics metrics) {
    this.sender = sender;
    this.fileLog = fileLog;
    this.reminders = reminders;
    this.notifications = notifications;
    this.properties = properties;
    this.time = time;
    this.metrics = metrics;
  }

  @PreDestroy
  void stopHeartbeats() {
    heartbeats.shutdownNow();
  }

  @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
  public void onMessage(MailSnapshot snapshot) {
    MDC.put("worker_id", workerId);
    MDC.put("appointment_id", snapshot.appointmentId().toString());
    if (snapshot.reminderId() != null) {
      MDC.put("reminder_id", snapshot.reminderId().toString());
    }
    if (snapshot.notificationId() != null) {
      MDC.put("notification_id", snapshot.notificationId().toString());
    }
    ScheduledFuture<?> beat = startHeartbeat(snapshot);
    try {
      if (snapshot.manual()) {
        sendManual(snapshot);
      } else {
        sendSystem(snapshot);
      }
    } catch (NotificationFailedException ex) {
      fail(snapshot, ex);
    } finally {
      if (beat != null) {
        beat.cancel(false);
      }
      MDC.remove("worker_id");
      MDC.remove("appointment_id");
      MDC.remove("reminder_id");
      MDC.remove("notification_id");
    }
  }

  private ScheduledFuture<?> startHeartbeat(MailSnapshot snapshot) {
    Duration lease = properties.getWorkers().getLease();
    if (snapshot.manual()) {
      return heartbeats.scheduleAtFixedRate(
          () -> notifications.heartbeatManual(snapshot.notificationId(), workerId, lease),
          5,
          5,
          TimeUnit.SECONDS);
    }
    return heartbeats.scheduleAtFixedRate(
        () -> reminders.heartbeat(snapshot.reminderId(), workerId, lease), 5, 5, TimeUnit.SECONDS);
  }

  private void sendSystem(MailSnapshot snapshot) throws NotificationFailedException {
    if (notifications.alreadySent(snapshot.idempotencyKey())) {
      if (reminders.heartbeat(snapshot.reminderId(), workerId, properties.getWorkers().getLease())
          && reminders.markSent(snapshot.reminderId(), workerId)) {
        return;
      }
      log.info("skip complete, reminder lease lost");
      metrics.leaseSkip();
      return;
    }
    if (!reminders.heartbeat(snapshot.reminderId(), workerId, properties.getWorkers().getLease())) {
      reminders.deferUntilProviderProof(snapshot.reminderId());
      log.info("skip send, waiting for provider webhook");
      metrics.leaseSkip();
      return;
    }
    if (notifications.providerAlreadyAccepted(snapshot.notificationId())) {
      reminders.markSent(snapshot.reminderId(), workerId);
      notifications.markSent(snapshot.idempotencyKey());
      log.info("skip send, provider already accepted");
      return;
    }
    deliver(snapshot);
    if (!reminders.markSent(snapshot.reminderId(), workerId)) {
      log.info("skip complete, reminder lease lost");
      metrics.leaseSkip();
      return;
    }
    notifications.markSent(snapshot.idempotencyKey());
    metrics.sent(latenessSeconds(snapshot));
    log.info("notification sent offset={}", snapshot.offsetLabel());
  }

  private void sendManual(MailSnapshot snapshot) throws NotificationFailedException {
    if (notifications.alreadySent(snapshot.idempotencyKey())) {
      return;
    }
    if (!notifications.heartbeatManual(
        snapshot.notificationId(), workerId, properties.getWorkers().getLease())) {
      notifications.deferManualUntilProviderProof(snapshot.notificationId());
      log.info("skip send, waiting for provider webhook");
      metrics.leaseSkip();
      return;
    }
    if (notifications.providerAlreadyAccepted(snapshot.notificationId())) {
      notifications.markSentManual(snapshot.notificationId(), workerId);
      log.info("skip send, provider already accepted");
      return;
    }
    deliver(snapshot);
    if (!notifications.markSentManual(snapshot.notificationId(), workerId)) {
      log.info("skip complete, notification lease lost");
      metrics.leaseSkip();
      return;
    }
    metrics.sent(0);
    log.info("notification sent offset={}", snapshot.offsetLabel());
  }

  private void deliver(MailSnapshot snapshot) throws NotificationFailedException {
    if (snapshot.fileOnly()) {
      fileLog.append(snapshot);
    } else {
      sender.send(snapshot, snapshot.notificationId(), notifications.correlationHeaders(snapshot));
    }
  }

  private void fail(MailSnapshot snapshot, NotificationFailedException ex) {
    int attempt = snapshot.attempts() + 1;
    boolean dead = RetryPolicy.permanent(ex) || RetryPolicy.deadLetter(attempt);
    if (snapshot.manual()) {
      boolean leased =
          dead
              ? notifications.markDeadManual(snapshot.notificationId(), workerId, ex.getMessage())
              : notifications.markRetryManual(
                  snapshot.notificationId(),
                  workerId,
                  RetryPolicy.nextAttempt(time.now(), attempt),
                  ex.getMessage());
      if (!leased) {
        log.info("skip complete, notification lease lost");
        metrics.leaseSkip();
        return;
      }
    } else {
      boolean leased =
          dead
              ? reminders.markDead(snapshot.reminderId(), workerId, ex.getMessage())
              : reminders.markRetry(
                  snapshot.reminderId(),
                  workerId,
                  RetryPolicy.nextAttempt(time.now(), attempt),
                  ex.getMessage());
      if (!leased) {
        log.info("skip complete, reminder lease lost");
        metrics.leaseSkip();
        return;
      }
      if (dead) {
        notifications.markDead(snapshot.idempotencyKey(), ex.getMessage());
      } else {
        notifications.markRetry(
            snapshot.idempotencyKey(),
            RetryPolicy.nextAttempt(time.now(), attempt),
            ex.getMessage());
      }
    }
    if (dead) {
      metrics.deadLetter();
    } else {
      metrics.retry();
    }
    log.warn(
        "notification failed offset={} transient={}",
        snapshot.offsetLabel(),
        ex.transientFailure());
  }

  private long latenessSeconds(MailSnapshot snapshot) {
    if (snapshot.offsetMinutes() == null) {
      return 0;
    }
    var due = snapshot.scheduledAt().minusSeconds(snapshot.offsetMinutes() * 60L);
    return Math.max(0, time.now().getEpochSecond() - due.getEpochSecond());
  }
}
