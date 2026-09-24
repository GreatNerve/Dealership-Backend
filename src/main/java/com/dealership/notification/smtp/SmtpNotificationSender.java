package com.dealership.notification.smtp;

import com.dealership.notification.MailSnapshot;
import com.dealership.notification.ReminderMail;
import com.dealership.shared.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "smtp")
public class SmtpNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

  private final JavaMailSender mail;
  private final AppProperties properties;

  public SmtpNotificationSender(JavaMailSender mail, AppProperties properties) {
    this.mail = mail;
    this.properties = properties;
  }

  @Override
  public void send(MailSnapshot snapshot, UUID correlationId, Map<String, String> headers)
      throws NotificationFailedException {
    ReminderMail body = ReminderMail.of(snapshot);
    try {
      MimeMessage message = mail.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(properties.getNotifications().getFrom(), "Dealership");
      helper.setTo(snapshot.contact());
      helper.setSubject(body.subject());
      helper.setText(body.text(), body.html());
      if (headers != null) {
        for (var header : headers.entrySet()) {
          message.setHeader(header.getKey(), header.getValue());
        }
      }
      mail.send(message);
      log.info(
          "smtp notification offset={} appointment_id={}",
          snapshot.offsetLabel(),
          snapshot.appointmentId());
    } catch (MailAuthenticationException ex) {
      throw new NotificationFailedException("smtp auth failed", false, ex);
    } catch (Exception ex) {
      if (SmtpFailures.invalidContact(ex)) {
        throw new NotificationFailedException("invalid contact", false, ex);
      }
      throw new NotificationFailedException("smtp failed", true, ex);
    }
  }
}
