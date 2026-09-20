package com.dealership.notification.smtp;

import com.dealership.notification.MailSnapshot;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.time.BookingTimes;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
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
  public void send(MailSnapshot snapshot) throws NotificationFailedException {
    String wall = BookingTimes.formatMail(snapshot.scheduledAt(), snapshot.displayOffset());
    try {
      MimeMessage message = mail.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(properties.getNotifications().getFrom());
      helper.setTo(snapshot.contact());
      helper.setSubject("Service appointment reminder — " + snapshot.dealershipName());
      helper.setText(
          "Your appointment at "
              + snapshot.dealershipName()
              + " is on "
              + wall
              + ".\n\nThis is your "
              + snapshot.offsetLabel()
              + " reminder.",
          false);
      mail.send(message);
      log.info(
          "smtp notification offset={} appointment_id={}",
          snapshot.offsetLabel(),
          snapshot.appointmentId());
    } catch (MailAuthenticationException ex) {
      throw new NotificationFailedException("smtp auth failed", false, ex);
    } catch (MailSendException ex) {
      throw new NotificationFailedException("smtp send failed", true, ex);
    } catch (Exception ex) {
      throw new NotificationFailedException("smtp failed", true, ex);
    }
  }
}
