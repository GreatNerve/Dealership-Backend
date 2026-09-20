package com.dealership.notification;

import com.dealership.notification.smtp.NotificationFailedException;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.time.BookingTimes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FileNotificationLog {

  private static final Logger log = LoggerFactory.getLogger(FileNotificationLog.class);

  private final Path file;
  private final List<RecordedSend> recorded = new CopyOnWriteArrayList<>();
  private final Object lock = new Object();

  public FileNotificationLog(AppProperties properties) {
    this.file = Path.of(properties.getNotifications().getLogDir()).resolve("notifications.log");
  }

  public void append(MailSnapshot snapshot) throws NotificationFailedException {
    String wall = BookingTimes.formatMail(snapshot.scheduledAt(), snapshot.displayOffset());
    String line =
        Instant.now()
            + " notify=false appointment_id="
            + snapshot.appointmentId()
            + " reminder_id="
            + snapshot.reminderId()
            + " offset="
            + snapshot.offsetLabel()
            + " when="
            + wall;
    try {
      synchronized (lock) {
        Path dir = file.getParent();
        if (dir != null) {
          Files.createDirectories(dir);
        }
        Files.writeString(
            file,
            line + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
    } catch (IOException ex) {
      throw new NotificationFailedException("notify-off log failed", true, ex);
    }
    recorded.add(new RecordedSend(snapshot.appointmentId(), snapshot.offsetMinutes(), wall));
    log.info(
        "notify-off file offset={} appointment_id={} path={}",
        snapshot.offsetLabel(),
        snapshot.appointmentId(),
        file);
  }

  public List<RecordedSend> recorded() {
    return List.copyOf(recorded);
  }

  public void clear() {
    recorded.clear();
  }

  public record RecordedSend(UUID appointmentId, int offsetMinutes, String wallTime) {}
}
