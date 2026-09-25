package com.dealership.reminder;

import com.dealership.shared.lifecycle.ShutdownGate;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

  private final ReminderService reminders;
  private final ShutdownGate shutdownGate;
  private final String workerId = "poller-" + UUID.randomUUID();

  public ReminderScheduler(ReminderService reminders, ShutdownGate shutdownGate) {
    this.reminders = reminders;
    this.shutdownGate = shutdownGate;
  }

  @Scheduled(fixedDelayString = "${app.workers.poll-interval}")
  public void tick() {
    if (!shutdownGate.acceptingClaims()) {
      return;
    }
    MDC.put("worker_id", workerId);
    try {
      reminders.pollDue(workerId);
    } finally {
      MDC.remove("worker_id");
    }
  }
}
