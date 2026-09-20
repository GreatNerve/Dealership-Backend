package com.dealership.reminder;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

  private final ReminderService reminders;
  private final String workerId = "poller-" + UUID.randomUUID();

  public ReminderScheduler(ReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(fixedDelayString = "${app.workers.poll-interval}")
  public void tick() {
    MDC.put("worker_id", workerId);
    try {
      reminders.pollDue(workerId);
    } finally {
      MDC.remove("worker_id");
    }
  }
}
