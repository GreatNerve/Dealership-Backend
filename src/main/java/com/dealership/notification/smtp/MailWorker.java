package com.dealership.notification.smtp;

import com.dealership.notification.FileNotificationLog;
import com.dealership.notification.MailSnapshot;
import com.dealership.notification.NotificationService;
import com.dealership.reminder.ReminderRepository;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.config.RabbitConfig;
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
  private final String workerId = "mail-" + UUID.randomUUID();
  private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

  public MailWorker(
      NotificationSender sender,
      FileNotificationLog fileLog,
      ReminderRepository reminders,
      NotificationService notifications,
      AppProperties properties,
      TimeProvider time) {
    this.sender = sender;
    this.fileLog = fileLog;
    this.reminders = reminders;
    this.notifications = notifications;
    this.properties = properties;
    this.time = time;
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
        if (!reminders.markSent(snapshot.reminderId())) {
          log.info("skip complete, reminder lease lost");
        }
        return;
      }
      if (!reminders.heartbeat(
          snapshot.reminderId(), workerId, properties.getWorkers().getLease())) {
        log.info("skip send, reminder not processing");
        return;
      }
      // notify false: assignment file log. notify true: stub or SMTP from mode.
      if (snapshot.fileOnly()) {
        fileLog.append(snapshot);
      } else {
        sender.send(snapshot);
      }
      if (!reminders.markSent(snapshot.reminderId())) {
        log.info("skip complete, reminder lease lost");
        return;
      }
      notifications.markSent(snapshot.idempotencyKey());
      log.info("notification sent offset={}", snapshot.offsetLabel());
    } catch (NotificationFailedException ex) {
      int attempt = snapshot.attempts() + 1;
      if (RetryPolicy.permanent(ex) || RetryPolicy.deadLetter(attempt)) {
        if (!reminders.markDead(snapshot.reminderId(), ex.getMessage())) {
          log.info("skip complete, reminder lease lost");
          return;
        }
        notifications.markDead(snapshot.idempotencyKey(), ex.getMessage());
      } else {
        var next = RetryPolicy.nextAttempt(time.now(), attempt);
        if (!reminders.markRetry(snapshot.reminderId(), next, ex.getMessage())) {
          log.info("skip complete, reminder lease lost");
          return;
        }
        notifications.markRetry(snapshot.idempotencyKey(), next, ex.getMessage());
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
}
