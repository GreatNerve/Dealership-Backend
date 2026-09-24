package com.dealership.notification;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

  private final NotificationService notifications;
  private final String workerId = "poller-" + UUID.randomUUID();

  public NotificationScheduler(NotificationService notifications) {
    this.notifications = notifications;
  }

  @Scheduled(fixedDelayString = "${app.workers.poll-interval}")
  public void tick() {
    MDC.put("worker_id", workerId);
    try {
      notifications.pollManualRetries(workerId);
    } finally {
      MDC.remove("worker_id");
    }
  }
}
