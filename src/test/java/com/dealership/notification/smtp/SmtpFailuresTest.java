package com.dealership.notification.smtp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.internet.AddressException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailParseException;

class SmtpFailuresTest {

  @Test
  void addressExceptionIsPermanentInvalidContact() {
    assertTrue(SmtpFailures.invalidContact(new AddressException("bad")));
    assertTrue(SmtpFailures.invalidContact(new MailParseException("bad")));
    assertTrue(SmtpFailures.invalidContact(new RuntimeException(new AddressException("nested"))));
    assertFalse(SmtpFailures.invalidContact(new RuntimeException("timeout")));
  }
}
