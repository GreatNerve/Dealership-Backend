package com.dealership.dealership;

import com.dealership.appointment.AppointmentService;
import com.dealership.notification.NotificationService;
import com.dealership.shared.api.InstantRange;
import com.dealership.shared.api.StatsBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

  private final HomeDealerships shops;
  private final AppointmentService appointments;
  private final NotificationService notifications;

  public DashboardService(
      HomeDealerships shops, AppointmentService appointments, NotificationService notifications) {
    this.shops = shops;
    this.appointments = appointments;
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public DashboardDtos.Stats stats(InstantRange range, StatsBucket bucket) {
    DealershipEntity shop = shops.requireStaffShop();
    StatsBucket slice = bucket == null ? StatsBucket.DAY : bucket;
    return new DashboardDtos.Stats(
        appointments.stats(shop, range, slice), notifications.stats(shop, range, slice));
  }
}
