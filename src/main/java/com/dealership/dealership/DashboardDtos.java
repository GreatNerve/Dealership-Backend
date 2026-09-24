package com.dealership.dealership;

import com.dealership.appointment.AppointmentDtos;
import com.dealership.notification.NotificationDtos;

public final class DashboardDtos {

  private DashboardDtos() {}

  public record Stats(AppointmentDtos.Stats appointments, NotificationDtos.Stats notifications) {}
}
