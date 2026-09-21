package com.dealership.notification.smtp;

import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailParseException;

final class SmtpFailures {

  private SmtpFailures() {}

  static boolean invalidContact(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof AddressException || cause instanceof MailParseException) {
        return true;
      }
    }
    return false;
  }
}
