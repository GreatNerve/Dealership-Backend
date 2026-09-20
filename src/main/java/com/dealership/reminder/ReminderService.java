package com.dealership.reminder;

import com.dealership.notification.NotificationService;
import com.dealership.shared.config.AppProperties;
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

  public ReminderService(
      ReminderRepository reminders, NotificationService notifications, AppProperties properties) {
    this.reminders = reminders;
    this.notifications = notifications;
    this.properties = properties;
  }

  public void insertForAppointment(UUID appointmentId) {
    for (int minutes : properties.getReminders().offsetMinutes()) {
      reminders.insertForAppointment(appointmentId, minutes);
    }
  }

  public void cancelUnsent(UUID appointmentId) {
    reminders.cancelUnsent(appointmentId);
  }

  @Transactional
  public void pollDue(String workerId) {
    reminders.expireClosedWindows();
    reminders.expireNoShows(properties.getReminders().getNoShowGrace());
    reminders
        .claimDue(workerId, properties.getWorkers().getLease())
        .ifPresent(
            claimed -> {
              MDC.put("reminder_id", claimed.id().toString());
              MDC.put("appointment_id", claimed.appointmentId().toString());
              reminders.loadMailFacts(claimed.id()).ifPresent(notifications::enqueueDue);
              log.info("reminder claimed offset={}", claimed.offsetMinutes());
              MDC.remove("reminder_id");
              MDC.remove("appointment_id");
            });
  }
}
