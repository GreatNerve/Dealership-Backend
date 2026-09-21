package com.dealership.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AppMetrics {

  private final Counter appointmentsCreated;
  private final Counter remindersClaimed;
  private final Counter notificationsSent;
  private final Counter notificationsRetry;
  private final Counter notificationsDead;
  private final Counter leaseSkips;
  private final Counter outboxPublished;
  private final DistributionSummary latenessSeconds;

  public AppMetrics(MeterRegistry registry) {
    this.appointmentsCreated = registry.counter("dealership.appointments.created");
    this.remindersClaimed = registry.counter("dealership.reminders.claimed");
    this.notificationsSent = registry.counter("dealership.notifications.sent");
    this.notificationsRetry = registry.counter("dealership.notifications.retry");
    this.notificationsDead = registry.counter("dealership.notifications.dead");
    this.leaseSkips = registry.counter("dealership.notifications.lease_skip");
    this.outboxPublished = registry.counter("dealership.outbox.published");
    this.latenessSeconds =
        DistributionSummary.builder("dealership.notifications.lateness")
            .baseUnit("seconds")
            .register(registry);
  }

  public void appointmentCreated() {
    appointmentsCreated.increment();
  }

  public void remindersClaimed(int count) {
    if (count > 0) {
      remindersClaimed.increment(count);
    }
  }

  public void sent(long latenessSecondsValue) {
    notificationsSent.increment();
    latenessSeconds.record(Math.max(0, latenessSecondsValue));
  }

  public void retry() {
    notificationsRetry.increment();
  }

  public void deadLetter() {
    notificationsDead.increment();
  }

  public void leaseSkip() {
    leaseSkips.increment();
  }

  public void outboxPublished() {
    outboxPublished.increment();
  }
}
