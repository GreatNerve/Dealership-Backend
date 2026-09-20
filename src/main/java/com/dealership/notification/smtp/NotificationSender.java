package com.dealership.notification.smtp;

import com.dealership.notification.MailSnapshot;

public interface NotificationSender {

  void send(MailSnapshot snapshot) throws NotificationFailedException;
}
