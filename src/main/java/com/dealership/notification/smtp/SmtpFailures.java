package com.dealership.notification.smtp;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

final class SmtpFailures {

  private SmtpFailures() {}

  static boolean invalidContact(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (isInvalidAddress(cause)) {
        return true;
      }
      if (cause instanceof MailSendException mail) {
        for (Exception failed : mail.getFailedMessages().values()) {
          if (isInvalidAddress(failed)) {
            return true;
          }
          for (Throwable nested = failed; nested != null; nested = nested.getCause()) {
            if (isInvalidAddress(nested)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isInvalidAddress(Throwable cause) {
    return cause instanceof AddressException
        || cause instanceof MailParseException
        || cause instanceof SendFailedException;
  }
}
