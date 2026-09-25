package com.dealership.notification.smtp;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

final class SmtpFailures {

  private SmtpFailures() {}

  static boolean invalidContact(Throwable ex) {
    if (transientReply(ex)) {
      return false;
    }
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

  // RFC 5321: 4yz is temporary (421/450/451/452, including a provider rate limit). Retry.
  private static boolean transientReply(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      int code = replyCode(cause);
      if (code >= 400 && code < 500) {
        return true;
      }
      if (cause instanceof MailSendException mail) {
        for (Exception failed : mail.getFailedMessages().values()) {
          if (transientReply(failed)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static int replyCode(Throwable cause) {
    if (cause instanceof SMTPSendFailedException smtp) {
      return smtp.getReturnCode();
    }
    if (cause instanceof SMTPAddressFailedException smtp) {
      return smtp.getReturnCode();
    }
    if (cause instanceof SMTPSenderFailedException smtp) {
      return smtp.getReturnCode();
    }
    return -1;
  }

  private static boolean isInvalidAddress(Throwable cause) {
    return cause instanceof AddressException
        || cause instanceof MailParseException
        || cause instanceof SendFailedException;
  }
}
