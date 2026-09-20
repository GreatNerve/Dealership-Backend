package com.dealership.notification.smtp;

import com.dealership.notification.MailSnapshot;
import com.dealership.shared.time.BookingTimes;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "stub", matchIfMissing = true)
public class StubNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(StubNotificationSender.class);

  private final List<RecordedSend> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(MailSnapshot snapshot) {
    String wall = BookingTimes.formatMail(snapshot.scheduledAt(), snapshot.displayOffset());
    sent.add(
        new RecordedSend(
            snapshot.offsetMinutes(),
            snapshot.appointmentId(),
            snapshot.idempotencyKey(),
            Instant.now(),
            wall,
            false));
    log.info(
        "stub notification offset={} appointment_id={} when={}",
        snapshot.offsetLabel(),
        snapshot.appointmentId(),
        wall);
  }

  public List<RecordedSend> recorded() {
    return List.copyOf(sent);
  }

  public void clear() {
    sent.clear();
  }

  public record RecordedSend(
      int offsetMinutes,
      java.util.UUID appointmentId,
      String idempotencyKey,
      Instant timestamp,
      String wallTime,
      boolean threw) {}
}
