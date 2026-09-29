package com.dealership.dealership;

import com.dealership.appointment.AppointmentEntity;
import com.dealership.appointment.AppointmentRepository;
import com.dealership.appointment.AppointmentStatus;
import com.dealership.dealership.DealershipDtos.OverrideResponse;
import com.dealership.dealership.DealershipDtos.OverrideWriteRequest;
import com.dealership.dealership.DealershipDtos.ScheduleResponse;
import com.dealership.dealership.DealershipDtos.ScheduleWriteRequest;
import com.dealership.dealership.DealershipDtos.SlotItem;
import com.dealership.dealership.DealershipDtos.SlotsResponse;
import com.dealership.dealership.DealershipDtos.WeekdayHours;
import com.dealership.dealership.ServiceSlots.OverrideWindow;
import com.dealership.dealership.ServiceSlots.WeekHours;
import com.dealership.identity.Role;
import com.dealership.shared.access.ResourceAccess;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.InstantRange;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import com.dealership.shared.time.TimeProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceSlotService {

  private final DealershipRepository dealerships;
  private final DealershipHoursRepository hoursRows;
  private final DealershipCapacityOverrideRepository overrides;
  private final AppointmentRepository appointments;
  private final HomeDealerships homeDealerships;
  private final AppProperties properties;
  private final TimeProvider time;
  private final NamedParameterJdbcTemplate jdbc;

  public ServiceSlotService(
      DealershipRepository dealerships,
      DealershipHoursRepository hoursRows,
      DealershipCapacityOverrideRepository overrides,
      AppointmentRepository appointments,
      HomeDealerships homeDealerships,
      AppProperties properties,
      TimeProvider time,
      NamedParameterJdbcTemplate jdbc) {
    this.dealerships = dealerships;
    this.hoursRows = hoursRows;
    this.overrides = overrides;
    this.appointments = appointments;
    this.homeDealerships = homeDealerships;
    this.properties = properties;
    this.time = time;
    this.jdbc = jdbc;
  }

  public void seedOnCreate(DealershipEntity shop) {
    boolean allDay = properties.getAppointments().isSeed24h();
    for (int weekday = 1; weekday <= 7; weekday++) {
      DealershipHoursEntity row = new DealershipHoursEntity();
      row.setDealershipId(shop.getId());
      row.setWeekday(weekday);
      if (allDay) {
        row.setClosed(false);
        row.setOpenTime(LocalTime.MIN);
        row.setCloseTime(LocalTime.MIN);
      } else if (weekday == 7) {
        row.setClosed(true);
      } else {
        row.setClosed(false);
        row.setOpenTime(LocalTime.of(9, 0));
        row.setCloseTime(LocalTime.of(18, 0));
      }
      hoursRows.save(row);
    }
  }

  @Transactional(readOnly = true)
  public ScheduleResponse getSchedule(UUID dealershipId) {
    DealershipEntity shop = requireReadable(dealershipId);
    return toSchedule(shop);
  }

  @Transactional
  public ScheduleResponse putSchedule(UUID dealershipId, ScheduleWriteRequest body) {
    DealershipEntity shop = requireHome(dealershipId);
    List<WeekHours> nextHours = parseHours(body);
    int capacity = body.defaultCapacity();
    if (capacity < 0) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "defaultCapacity must be >= 0");
    }
    List<OverrideWindow> windows = overrideWindows(shop.getId());
    assertScheduleFits(shop, nextHours, capacity, windows);
    shop.setDefaultCapacity(capacity);
    dealerships.save(shop);
    hoursRows.deleteByDealershipId(shop.getId());
    hoursRows.flush();
    for (WeekHours hour : nextHours) {
      DealershipHoursEntity row = new DealershipHoursEntity();
      row.setDealershipId(shop.getId());
      row.setWeekday(hour.weekday());
      row.setClosed(hour.closed());
      row.setOpenTime(hour.open());
      row.setCloseTime(hour.close());
      hoursRows.save(row);
    }
    return toSchedule(shop);
  }

  @Transactional(readOnly = true)
  public List<OverrideResponse> listOverrides(UUID dealershipId) {
    requireHome(dealershipId);
    return overrides.findByDealershipIdOrderByFromDateAsc(dealershipId).stream()
        .map(OverrideResponse::from)
        .toList();
  }

  @Transactional
  public OverrideResponse createOverride(UUID dealershipId, OverrideWriteRequest body) {
    DealershipEntity shop = requireHome(dealershipId);
    OverrideWindow next = parseOverride(body);
    assertNoOverlap(shop.getId(), next, null);
    assertOverrideFits(shop, next);
    DealershipCapacityOverrideEntity row = new DealershipCapacityOverrideEntity();
    applyOverride(row, shop.getId(), next);
    overrides.save(row);
    return OverrideResponse.from(row);
  }

  @Transactional
  public OverrideResponse updateOverride(
      UUID dealershipId, UUID overrideId, OverrideWriteRequest body) {
    DealershipEntity shop = requireHome(dealershipId);
    DealershipCapacityOverrideEntity row =
        overrides
            .findByIdAndDealershipId(overrideId, shop.getId())
            .orElseThrow(ApiException::notFound);
    OverrideWindow next = parseOverride(body);
    assertNoOverlap(shop.getId(), next, row.getId());
    assertOverrideFits(shop, next);
    applyOverride(row, shop.getId(), next);
    overrides.save(row);
    return OverrideResponse.from(row);
  }

  @Transactional
  public void deleteOverride(UUID dealershipId, UUID overrideId) {
    DealershipEntity shop = requireHome(dealershipId);
    DealershipCapacityOverrideEntity row =
        overrides
            .findByIdAndDealershipId(overrideId, shop.getId())
            .orElseThrow(ApiException::notFound);
    overrides.delete(row);
  }

  @Transactional(readOnly = true)
  public SlotsResponse listSlots(UUID dealershipId, InstantRange range) {
    DealershipEntity shop = requireReadable(dealershipId);
    ZoneId zone = ZoneId.of(shop.getTimezone());
    Instant now = time.now();
    Instant from = range.from();
    Instant to = range.to();
    AuthPrincipal user = CurrentUser.require();
    if (user.role() == Role.CUSTOMER) {
      Instant max =
          ServiceSlots.advanceExclusiveEnd(
              now, zone, properties.getAppointments().getMaxAdvanceDays());
      if (to.isAfter(max)) {
        to = max;
      }
    }
    if (!from.isBefore(to)) {
      return new SlotsResponse(List.of());
    }
    LocalDate fromDay = from.atZone(zone).toLocalDate();
    LocalDate toDay = to.atZone(zone).minusNanos(1).toLocalDate();
    if (fromDay.plusDays(40).isBefore(toDay)) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "slot range cannot exceed 40 days");
    }
    List<WeekHours> hours = weekHours(shop.getId());
    List<OverrideWindow> windows = overrideWindows(shop.getId());
    Map<Instant, Long> booked = bookedBySlot(shop.getId());
    List<SlotItem> items = new ArrayList<>();
    for (LocalDate day = fromDay; !day.isAfter(toDay); day = day.plusDays(1)) {
      WeekHours hour = hoursFor(hours, day.getDayOfWeek().getValue());
      if (hour == null) {
        continue;
      }
      for (Instant start :
          ServiceSlots.startsOnDay(
              day, zone, hour, properties.getAppointments().slotDurationMinutes())) {
        if (start.isBefore(from) || !start.isBefore(to)) {
          continue;
        }
        ZonedDateTime local = start.atZone(zone);
        int capacity = ServiceSlots.effectiveCapacity(local, shop.getDefaultCapacity(), windows);
        long taken = booked.getOrDefault(start, 0L);
        long available = start.isBefore(now) ? 0 : Math.max(0, capacity - taken);
        items.add(new SlotItem(start, capacity, taken, available));
      }
    }
    return new SlotsResponse(items);
  }

  public void assertBookable(DealershipEntity shop, Instant slot, UUID excludeAppointmentId) {
    ZoneId zone = ZoneId.of(shop.getTimezone());
    int duration = properties.getAppointments().slotDurationMinutes();
    if (!ServiceSlots.aligned(slot, zone, duration)) {
      throw ApiException.of(
          ApiErrorCode.NOT_A_SERVICE_SLOT, "scheduledAt must land on a Service Slot");
    }
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.CUSTOMER) {
      return;
    }
    ZonedDateTime local = slot.atZone(zone);
    if (!ServiceSlots.insideHours(local, weekHours(shop.getId()))) {
      throw ApiException.of(ApiErrorCode.OUTSIDE_HOURS, "scheduledAt is outside Dealership hours");
    }
    Instant now = time.now();
    if (ServiceSlots.tooFarAhead(
        slot, now, zone, properties.getAppointments().getMaxAdvanceDays())) {
      throw ApiException.of(
          ApiErrorCode.TOO_FAR_AHEAD, "scheduledAt is past the Dealership booking window");
    }
    // Last seat: serialize Customer creates for this Service Slot Instant.
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
        new MapSqlParameterSource("key", shop.getId() + "@" + slot.toEpochMilli()),
        rs -> null);
    int capacity =
        ServiceSlots.effectiveCapacity(
            local, shop.getDefaultCapacity(), overrideWindows(shop.getId()));
    UUID exclude =
        excludeAppointmentId != null
            ? excludeAppointmentId
            : UUID.fromString("00000000-0000-4000-8000-000000000000");
    long booked =
        appointments.countConfirmedAt(shop.getId(), AppointmentStatus.CONFIRMED, slot, exclude);
    if (booked >= capacity) {
      throw ApiException.of(ApiErrorCode.SLOT_FULL, "This Service Slot is full");
    }
  }

  private DealershipEntity requireReadable(UUID dealershipId) {
    DealershipEntity shop = dealerships.findById(dealershipId).orElseThrow(ApiException::notFound);
    AuthPrincipal user = CurrentUser.require();
    if (user.role() == Role.DEALERSHIP_STAFF) {
      ResourceAccess.requireVisible(
          homeDealerships.requireStaffShop().getId().equals(dealershipId));
    }
    return shop;
  }

  private DealershipEntity requireHome(UUID dealershipId) {
    DealershipEntity home = homeDealerships.requireStaffShop();
    ResourceAccess.requireVisible(home.getId().equals(dealershipId));
    return home;
  }

  private ScheduleResponse toSchedule(DealershipEntity shop) {
    List<WeekdayHours> hours =
        hoursRows.findByDealershipIdOrderByWeekdayAsc(shop.getId()).stream()
            .map(
                row ->
                    new WeekdayHours(
                        row.getWeekday(), row.isClosed(), row.getOpenTime(), row.getCloseTime()))
            .toList();
    return new ScheduleResponse(
        shop.getDefaultCapacity(),
        properties.getAppointments().slotDurationMinutes(),
        properties.getAppointments().getMaxAdvanceDays(),
        hours);
  }

  private List<WeekHours> weekHours(UUID shopId) {
    return hoursRows.findByDealershipIdOrderByWeekdayAsc(shopId).stream()
        .map(
            row ->
                new WeekHours(
                    row.getWeekday(), row.isClosed(), row.getOpenTime(), row.getCloseTime()))
        .toList();
  }

  private List<OverrideWindow> overrideWindows(UUID shopId) {
    return overrides.findByDealershipIdOrderByFromDateAsc(shopId).stream()
        .map(ServiceSlotService::toWindow)
        .toList();
  }

  private static OverrideWindow toWindow(DealershipCapacityOverrideEntity row) {
    return new OverrideWindow(
        row.getFromDate(), row.getToDate(), row.getFromTime(), row.getToTime(), row.getCapacity());
  }

  private static WeekHours hoursFor(List<WeekHours> hours, int weekday) {
    for (WeekHours hour : hours) {
      if (hour.weekday() == weekday) {
        return hour;
      }
    }
    return null;
  }

  private List<WeekHours> parseHours(ScheduleWriteRequest body) {
    if (body.hours() == null || body.hours().size() != 7) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "hours must include all 7 weekdays");
    }
    boolean[] seen = new boolean[8];
    List<WeekHours> out = new ArrayList<>();
    for (WeekdayHours hour : body.hours()) {
      if (hour.weekday() < 1 || hour.weekday() > 7 || seen[hour.weekday()]) {
        throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "weekday must be unique 1–7");
      }
      seen[hour.weekday()] = true;
      if (hour.closed()) {
        out.add(new WeekHours(hour.weekday(), true, null, null));
        continue;
      }
      if (hour.openTime() == null || hour.closeTime() == null) {
        throw ApiException.of(
            ApiErrorCode.VALIDATION_ERROR, "openTime and closeTime are required when open");
      }
      if (!hour.openTime().equals(LocalTime.MIN) || !hour.closeTime().equals(LocalTime.MIN)) {
        if (!hour.openTime().isBefore(hour.closeTime())) {
          throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "closeTime must be after openTime");
        }
      }
      out.add(new WeekHours(hour.weekday(), false, hour.openTime(), hour.closeTime()));
    }
    return out;
  }

  private static OverrideWindow parseOverride(OverrideWriteRequest body) {
    if (body.fromDate() == null || body.toDate() == null) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "fromDate and toDate are required");
    }
    if (body.toDate().isBefore(body.fromDate())) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "toDate must be on or after fromDate");
    }
    if (body.capacity() < 0) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "capacity must be >= 0");
    }
    boolean hasFrom = body.fromTime() != null;
    boolean hasTo = body.toTime() != null;
    if (hasFrom != hasTo) {
      throw ApiException.of(
          ApiErrorCode.VALIDATION_ERROR, "fromTime and toTime must both be set or both omitted");
    }
    if (hasFrom && !body.fromTime().isBefore(body.toTime())) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "toTime must be after fromTime");
    }
    return new OverrideWindow(
        body.fromDate(), body.toDate(), body.fromTime(), body.toTime(), body.capacity());
  }

  private static void applyOverride(
      DealershipCapacityOverrideEntity row, UUID shopId, OverrideWindow next) {
    row.setDealershipId(shopId);
    row.setFromDate(next.fromDate());
    row.setToDate(next.toDate());
    row.setFromTime(next.fromTime());
    row.setToTime(next.toTime());
    row.setCapacity(next.capacity());
  }

  private void assertNoOverlap(UUID shopId, OverrideWindow next, UUID ignoreId) {
    for (DealershipCapacityOverrideEntity row :
        overrides.findByDealershipIdOrderByFromDateAsc(shopId)) {
      if (ignoreId != null && ignoreId.equals(row.getId())) {
        continue;
      }
      if (ServiceSlots.overlaps(next, toWindow(row))) {
        throw ApiException.of(
            ApiErrorCode.OVERRIDE_OVERLAPS, "Capacity Override overlaps an existing range");
      }
    }
  }

  private void assertScheduleFits(
      DealershipEntity shop,
      List<WeekHours> nextHours,
      int defaultCapacity,
      List<OverrideWindow> windows) {
    Map<Instant, Long> booked = bookedBySlot(shop.getId());
    ZoneId zone = ZoneId.of(shop.getTimezone());
    for (Map.Entry<Instant, Long> e : booked.entrySet()) {
      ZonedDateTime local = e.getKey().atZone(zone);
      if (!ServiceSlots.insideHours(local, nextHours)) {
        throw ApiException.of(
            ApiErrorCode.SCHEDULE_CONFLICT, "Confirmed Appointments fall outside the new hours");
      }
      int capacity = ServiceSlots.effectiveCapacity(local, defaultCapacity, windows);
      if (e.getValue() > capacity) {
        throw ApiException.of(
            ApiErrorCode.SCHEDULE_CONFLICT, "Confirmed Appointments exceed the new Slot Capacity");
      }
    }
  }

  private void assertOverrideFits(DealershipEntity shop, OverrideWindow next) {
    Map<Instant, Long> booked = bookedBySlot(shop.getId());
    ZoneId zone = ZoneId.of(shop.getTimezone());
    for (Map.Entry<Instant, Long> e : booked.entrySet()) {
      ZonedDateTime local = e.getKey().atZone(zone);
      if (!ServiceSlots.covers(next, local.toLocalDate(), local.toLocalTime())) {
        continue;
      }
      if (e.getValue() > next.capacity()) {
        throw ApiException.of(
            ApiErrorCode.SCHEDULE_CONFLICT,
            "Confirmed Appointments exceed the override Slot Capacity");
      }
    }
  }

  private Map<Instant, Long> bookedBySlot(UUID shopId) {
    Map<Instant, Long> booked = new HashMap<>();
    for (AppointmentEntity row :
        appointments.findByDealershipIdAndStatus(shopId, AppointmentStatus.CONFIRMED)) {
      booked.merge(row.getScheduledAt(), 1L, Long::sum);
    }
    return booked;
  }
}
