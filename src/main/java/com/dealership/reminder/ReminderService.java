package com.dealership.reminder;

import com.dealership.notification.NotificationService;
import com.dealership.notification.smtp.RetryPolicy;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.metrics.AppMetrics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

  private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

  private final ReminderRepository reminders;
  private final NotificationService notifications;
  private final AppProperties properties;
  private final AppMetrics metrics;

  public ReminderService(
      ReminderRepository reminders,
      NotificationService notifications,
      AppProperties properties,
      AppMetrics metrics) {
    this.reminders = reminders;
    this.notifications = notifications;
    this.properties = properties;
    this.metrics = metrics;
  }

  public void insertForAppointment(UUID appointmentId) {
    int version = reminders.nextScheduleVersion(appointmentId);
    List<Integer> offsets = new ArrayList<>(properties.getReminders().offsetMinutes());
    offsets.sort(Comparator.reverseOrder());
    for (int i = 0; i < offsets.size(); i++) {
      Integer next = i + 1 < offsets.size() ? offsets.get(i + 1) : null;
      reminders.insertForAppointment(appointmentId, offsets.get(i), next, version);
    }
  }

  public List<ReminderRepository.ReminderRow> history(UUID appointmentId) {
    return reminders.listHistory(appointmentId);
  }

  public void cancelUnsent(UUID appointmentId) {
    reminders.cancelUnsent(appointmentId);
  }

  @Transactional
  public void pollDue(String workerId) {
    reminders.expireClosedWindows();
    reminders.expireNoShows(properties.getReminders().getNoShowGrace());
    var claimed =
        reminders.claimDue(
            workerId,
            properties.getWorkers().getLease(),
            RetryPolicy.MIN_DELAY,
            properties.getWorkers().getClaimBatch());
    if (claimed.isEmpty()) {
      return;
    }
    metrics.remindersClaimed(claimed.size());
    for (var facts :
        reminders.loadMailFacts(
            claimed.stream().map(ReminderRepository.ClaimedReminder::id).toList())) {
      MDC.put("reminder_id", facts.reminderId().toString());
      MDC.put("appointment_id", facts.appointmentId().toString());
      notifications.enqueueDue(facts);
      log.info("reminder claimed offset={}", facts.offsetMinutes());
      MDC.remove("reminder_id");
      MDC.remove("appointment_id");
    }
  }
}
