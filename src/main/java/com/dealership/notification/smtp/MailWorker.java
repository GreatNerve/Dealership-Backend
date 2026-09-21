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
    MDC.put("reminder_id", snapshot.reminderId().toString());
    // SMTP timeout is shorter than the lease so a slow send cannot be stolen mid-flight.
    ScheduledFuture<?> beat =
        heartbeats.scheduleAtFixedRate(
            () ->
                reminders.heartbeat(
                    snapshot.reminderId(), workerId, properties.getWorkers().getLease()),
            5,
            5,
            TimeUnit.SECONDS);
    try {
      if (notifications.alreadySent(snapshot.idempotencyKey())) {
        if (reminders.heartbeat(snapshot.reminderId(), workerId, properties.getWorkers().getLease())
            && reminders.markSent(snapshot.reminderId(), workerId)) {
          return;
        }
        log.info("skip complete, reminder lease lost");
        metrics.leaseSkip();
        return;
      }
      if (!reminders.heartbeat(
          snapshot.reminderId(), workerId, properties.getWorkers().getLease())) {
        log.info("skip send, reminder not processing");
        metrics.leaseSkip();
        return;
      }
      // notify false: assignment file log. notify true: stub or SMTP from mode.
      if (snapshot.fileOnly()) {
        fileLog.append(snapshot);
      } else {
        sender.send(snapshot);
      }
      if (!reminders.markSent(snapshot.reminderId(), workerId)) {
        log.info("skip complete, reminder lease lost");
        metrics.leaseSkip();
        return;
      }
      notifications.markSent(snapshot.idempotencyKey());
      metrics.sent(latenessSeconds(snapshot));
      log.info("notification sent offset={}", snapshot.offsetLabel());
    } catch (NotificationFailedException ex) {
      int attempt = snapshot.attempts() + 1;
      if (RetryPolicy.permanent(ex) || RetryPolicy.deadLetter(attempt)) {
        if (!reminders.markDead(snapshot.reminderId(), workerId, ex.getMessage())) {
          log.info("skip complete, reminder lease lost");
          metrics.leaseSkip();
          return;
        }
        notifications.markDead(snapshot.idempotencyKey(), ex.getMessage());
        metrics.deadLetter();
      } else {
        var next = RetryPolicy.nextAttempt(time.now(), attempt);
        if (!reminders.markRetry(snapshot.reminderId(), workerId, next, ex.getMessage())) {
          log.info("skip complete, reminder lease lost");
          metrics.leaseSkip();
          return;
        }
        notifications.markRetry(snapshot.idempotencyKey(), next, ex.getMessage());
        metrics.retry();
      }
      log.warn(
          "notification failed offset={} transient={}",
          snapshot.offsetLabel(),
          ex.transientFailure());
    } finally {
      beat.cancel(false);
      MDC.remove("worker_id");
      MDC.remove("appointment_id");
      MDC.remove("reminder_id");
    }
  }

  private long latenessSeconds(MailSnapshot snapshot) {
    var due = snapshot.scheduledAt().minusSeconds(snapshot.offsetMinutes() * 60L);
    return Math.max(0, time.now().getEpochSecond() - due.getEpochSecond());
  }
}
