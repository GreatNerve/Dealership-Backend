package com.dealership.notification;

import com.dealership.shared.time.BookingTimes;

public record ReminderMail(String subject, String text, String html) {

  public static ReminderMail of(MailSnapshot snapshot) {
    String shop = snapshot.dealershipName() == null ? "the dealership" : snapshot.dealershipName();
    String localDate =
        BookingTimes.formatMailDate(snapshot.scheduledAt(), snapshot.displayOffset());
    String localClock =
        BookingTimes.formatMailClock(snapshot.scheduledAt(), snapshot.displayOffset());
    String vehicle = vehicleLine(snapshot);
    String subject = "Service appointment — " + shop;
    String text =
        shop
            + "\n\nVehicle: "
            + vehicle
            + "\n"
            + localClock
            + "\n"
            + localDate
            + "\n\nPlease arrive a few minutes early.\n";
    String html =
        """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>%s</title>
</head>
<body style="margin:0;padding:0;background:#efe8dc;font-family:Georgia,'Times New Roman',serif;color:#1c1917;">
  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#efe8dc;padding:32px 12px;">
    <tr>
      <td align="center">
        <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%%;background:#fffaf3;border:1px solid #d9cbb8;">
          <tr>
            <td style="background:#1c1917;padding:28px 32px;">
              <p style="margin:0;font-size:11px;letter-spacing:0.22em;text-transform:uppercase;color:#c4b5a0;">Service appointment</p>
              <h1 style="margin:10px 0 0;font-size:26px;font-weight:normal;color:#fffaf3;">%s</h1>
            </td>
          </tr>
          <tr>
            <td style="padding:28px 32px 12px;font-size:16px;line-height:1.55;color:#44403c;">
              Vehicle: <strong style="color:#1c1917;">%s</strong>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 32px 28px;">
              <p style="margin:0;padding:18px 20px;background:#1c1917;color:#fffaf3;font-size:22px;line-height:1.4;">%s<br><span style="font-size:16px;color:#c4b5a0;">%s</span></p>
            </td>
          </tr>
          <tr>
            <td style="padding:0 32px 28px;font-size:15px;line-height:1.55;color:#44403c;">
              Please arrive a few minutes early.
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
"""
            .formatted(esc(subject), esc(shop), esc(vehicle), esc(localClock), esc(localDate));
    return new ReminderMail(subject, text, html);
  }

  static String vehicleLine(MailSnapshot snapshot) {
    StringBuilder line = new StringBuilder();
    if (snapshot.vehicleYear() != null) {
      line.append(snapshot.vehicleYear()).append(' ');
    }
    if (snapshot.vehicleMake() != null && !snapshot.vehicleMake().isBlank()) {
      line.append(snapshot.vehicleMake().trim()).append(' ');
    }
    if (snapshot.vehicleModel() != null && !snapshot.vehicleModel().isBlank()) {
      line.append(snapshot.vehicleModel().trim());
    }
    String body = line.toString().trim();
    String plate = snapshot.registrationNumber();
    if (plate != null && !plate.isBlank()) {
      if (body.isEmpty()) {
        return plate.trim();
      }
      return body + " · " + plate.trim();
    }
    return body.isEmpty() ? "your vehicle" : body;
  }

  private static String esc(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
