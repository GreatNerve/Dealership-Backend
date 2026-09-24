package com.dealership.notification.smtp;

import com.dealership.notification.MailSnapshot;
import java.util.Map;
import java.util.UUID;

public interface NotificationSender {

  void send(MailSnapshot snapshot, UUID correlationId, Map<String, String> headers)
      throws NotificationFailedException;
}
