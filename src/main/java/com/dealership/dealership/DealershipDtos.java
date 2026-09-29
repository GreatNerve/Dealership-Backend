package com.dealership.dealership;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class DealershipDtos {

  private DealershipDtos() {}

  public record DealershipResponse(UUID id, String name, String timezone, String address) {
    public static DealershipResponse from(DealershipEntity entity) {
      return new DealershipResponse(
          entity.getId(), entity.getName(), entity.getTimezone(), entity.getAddress());
    }
  }

  public record WeekdayHours(
      int weekday, boolean closed, LocalTime openTime, LocalTime closeTime) {}

  public record ScheduleResponse(
      int defaultCapacity, int slotDurationMinutes, int maxAdvanceDays, List<WeekdayHours> hours) {}

  public record ScheduleWriteRequest(int defaultCapacity, List<WeekdayHours> hours) {}

  public record OverrideResponse(
      UUID id,
      LocalDate fromDate,
      LocalDate toDate,
      LocalTime fromTime,
      LocalTime toTime,
      int capacity) {
    public static OverrideResponse from(DealershipCapacityOverrideEntity entity) {
      return new OverrideResponse(
          entity.getId(),
          entity.getFromDate(),
          entity.getToDate(),
          entity.getFromTime(),
          entity.getToTime(),
          entity.getCapacity());
    }
  }

  public record OverrideWriteRequest(
      LocalDate fromDate, LocalDate toDate, LocalTime fromTime, LocalTime toTime, int capacity) {}

  public record SlotItem(Instant start, int capacity, long booked, long available) {}

  public record SlotsResponse(List<SlotItem> items) {}
}
