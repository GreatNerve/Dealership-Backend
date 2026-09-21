package com.dealership.notification.smtp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

class SmtpFailuresTest {

  @Test
  void addressExceptionIsPermanentInvalidContact() {
    assertTrue(SmtpFailures.invalidContact(new AddressException("bad")));
    assertTrue(SmtpFailures.invalidContact(new MailParseException("bad")));
    assertTrue(SmtpFailures.invalidContact(new RuntimeException(new AddressException("nested"))));
    assertFalse(SmtpFailures.invalidContact(new RuntimeException("timeout")));
    assertTrue(SmtpFailures.invalidContact(new SendFailedException("invalid")));
    MailSendException send =
        new MailSendException(Map.of("bad@x", new SendFailedException("invalid")));
    assertTrue(SmtpFailures.invalidContact(send));
  }
}
