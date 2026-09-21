package com.dealership.appointment;

import com.dealership.customer.CustomerDtos;
import com.dealership.customer.CustomerEntity;
import com.dealership.customer.CustomerRepository;
import com.dealership.dealership.DealershipDtos;
import com.dealership.dealership.DealershipEntity;
import com.dealership.dealership.DealershipRepository;
import com.dealership.dealership.DealershipStaffEntity;
import com.dealership.dealership.DealershipStaffRepository;
import com.dealership.identity.Role;
import com.dealership.identity.UserEntity;
import com.dealership.identity.UserRepository;
import com.dealership.notification.NotificationEntity;
import com.dealership.notification.NotificationRepository;
import com.dealership.reminder.ReminderRepository;
import com.dealership.reminder.ReminderService;
import com.dealership.shared.access.ResourceAccess;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageQuery;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.metrics.AppMetrics;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import com.dealership.shared.time.BookingInstant;
import com.dealership.shared.time.BookingTimes;
import com.dealership.shared.time.TimeProvider;
import com.dealership.vehicle.VehicleDtos;
import com.dealership.vehicle.VehicleEntity;
import com.dealership.vehicle.VehicleRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

  private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

  private final AppointmentRepository appointments;
  private final VehicleRepository vehicles;
  private final CustomerRepository customers;
  private final UserRepository users;
  private final DealershipRepository dealerships;
  private final DealershipStaffRepository staff;
  private final ReminderService reminders;
  private final NotificationRepository notifications;
  private final IdempotencyService idempotency;
  private final TimeProvider time;
  private final PageQueries pages;
  private final AppProperties properties;
  private final AppMetrics metrics;

  public AppointmentService(
      AppointmentRepository appointments,
      VehicleRepository vehicles,
      CustomerRepository customers,
      UserRepository users,
      DealershipRepository dealerships,
      DealershipStaffRepository staff,
      ReminderService reminders,
      NotificationRepository notifications,
      IdempotencyService idempotency,
      TimeProvider time,
      PageQueries pages,
      AppProperties properties,
      AppMetrics metrics) {
    this.appointments = appointments;
    this.vehicles = vehicles;
    this.customers = customers;
    this.users = users;
    this.dealerships = dealerships;
    this.staff = staff;
    this.reminders = reminders;
    this.notifications = notifications;
    this.idempotency = idempotency;
    this.time = time;
    this.pages = pages;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Transactional
  public AppointmentDtos.AppointmentResponse createCustomer(
      String idempotencyKey, AppointmentDtos.CustomerCreateRequest body) {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.CUSTOMER) {
      throw ApiException.forbidden("Customer body requires CUSTOMER role");
    }
    if (body.dealershipId() == null) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "dealershipId is required");
    }
    CustomerEntity customer =
        customers.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
    var begin = idempotency.begin(user.userId(), idempotencyKey, idempotency.fingerprint(body));
    if (begin.replay()) {
      return idempotency.replayBody(begin.row());
    }
    VehicleEntity vehicle = vehicles.findById(body.vehicleId()).orElseThrow(ApiException::notFound);
    ResourceAccess.requireVisible(vehicle.getCustomerId().equals(customer.getId()));
    DealershipEntity shop =
        dealerships.findById(body.dealershipId()).orElseThrow(ApiException::notFound);
    boolean notify = body.notifyEnabled() == null || body.notifyEnabled();
    return insertConfirmed(customer, vehicle, shop, body.scheduledAt(), notify, user, begin.row());
  }

  @Transactional
  public AppointmentDtos.AppointmentResponse createStaff(
      String idempotencyKey, AppointmentDtos.StaffCreateRequest body) {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Staff body requires DEALERSHIP_STAFF role");
    }
    if (body.dealershipId() != null) {
      throw ApiException.of(
          ApiErrorCode.STAFF_DEALERSHIP_FROM_HOME, "Staff must not send dealershipId");
    }
    if (body.customerId() == null) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, "customerId is required");
    }
    DealershipStaffEntity membership =
        staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
    DealershipEntity shop =
        dealerships.findById(membership.getDealershipId()).orElseThrow(ApiException::notFound);
    var begin = idempotency.begin(user.userId(), idempotencyKey, idempotency.fingerprint(body));
    if (begin.replay()) {
      return idempotency.replayBody(begin.row());
    }
    CustomerEntity customer =
        customers.findById(body.customerId()).orElseThrow(ApiException::notFound);
    VehicleEntity vehicle = vehicles.findById(body.vehicleId()).orElseThrow(ApiException::notFound);
    if (!vehicle.getCustomerId().equals(customer.getId())) {
      throw ApiException.of(
          ApiErrorCode.VEHICLE_NOT_OWNED, "Vehicle does not belong to this Customer");
    }
    boolean notify = body.notifyEnabled() == null || body.notifyEnabled();
    return insertConfirmed(customer, vehicle, shop, body.scheduledAt(), notify, user, begin.row());
  }

  private AppointmentDtos.AppointmentResponse insertConfirmed(
      CustomerEntity customer,
      VehicleEntity vehicle,
      DealershipEntity shop,
      String scheduledAtRaw,
      boolean notify,
      AuthPrincipal user,
      IdempotencyKeyEntity idempotencyRow) {
    BookingInstant booking = BookingTimes.parseScheduledAt(scheduledAtRaw);
    if (AppointmentPolicies.scheduledAtIsPast(booking.utc(), time.now())) {
      throw ApiException.of(ApiErrorCode.SCHEDULED_AT_PAST, "scheduledAt must be in the future");
    }
    AppointmentEntity appointment = new AppointmentEntity();
    appointment.setCustomerId(customer.getId());
    appointment.setVehicleId(vehicle.getId());
    appointment.setDealershipId(shop.getId());
    appointment.setScheduledAt(booking.utc());
    appointment.setDisplayOffset(booking.displayOffset().getId());
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setCreatedByUserId(user.userId());
    appointment.setCreatedByRole(user.role());
    appointment.setNotify(notify);
    appointment.setOneConfirmed(properties.getAppointments().isOneConfirmedPerVehicle());
    try {
      appointments.saveAndFlush(appointment);
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.of(
          ApiErrorCode.VEHICLE_ALREADY_CONFIRMED,
          "This vehicle already has a confirmed appointment");
    }
    reminders.insertForAppointment(appointment.getId());
    metrics.appointmentCreated();
    MDC.put("appointment_id", appointment.getId().toString());
    log.info("appointment created");
    MDC.remove("appointment_id");
    AppointmentDtos.AppointmentResponse response =
        toResponse(appointment, customer, vehicle, shop, user.role(), nameOf(customer));
    idempotency.complete(idempotencyRow, appointment.getId(), response);
    return response;
  }

  @Transactional
  public AppointmentDtos.AppointmentResponse cancel(UUID id) {
    return closeConfirmed(id, AppointmentStatus.CANCELLED, "cancelled");
  }

  @Transactional
  public AppointmentDtos.AppointmentResponse complete(UUID id) {
    requireStaff();
    return closeConfirmed(id, AppointmentStatus.COMPLETED, "completed");
  }

  @Transactional
  public AppointmentDtos.AppointmentResponse reschedule(
      UUID id, AppointmentDtos.RescheduleRequest body) {
    AuthPrincipal user = CurrentUser.require();
    VisibleRow row = loadVisible(id, user);
    AppointmentEntity appointment = row.appointment();
    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
      throw ApiException.of(
          ApiErrorCode.NOT_CONFIRMED, "Only a Confirmed Appointment can be rescheduled");
    }
    BookingInstant booking = BookingTimes.parseScheduledAt(body.scheduledAt());
    if (AppointmentPolicies.scheduledAtIsPast(booking.utc(), time.now())) {
      throw ApiException.of(ApiErrorCode.SCHEDULED_AT_PAST, "scheduledAt must be in the future");
    }
    reminders.cancelUnsent(appointment.getId());
    appointment.setScheduledAt(booking.utc());
    appointment.setDisplayOffset(booking.displayOffset().getId());
    appointments.saveAndFlush(appointment);
    reminders.insertForAppointment(appointment.getId());
    return toResponse(row, user.role());
  }

  @Transactional(readOnly = true)
  public AppointmentDtos.AppointmentResponse get(UUID id) {
    AuthPrincipal user = CurrentUser.require();
    return toResponse(loadVisible(id, user), user.role());
  }

  @Transactional(readOnly = true)
  public List<AppointmentDtos.ReminderItem> reminders(UUID id) {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can read Reminders");
    }
    loadVisible(id, user);
    List<ReminderRepository.ReminderRow> rows = reminders.currentVersion(id);
    Map<UUID, NotificationEntity> notes =
        rows.isEmpty()
            ? Map.of()
            : notifications
                .findByReminderIdIn(rows.stream().map(ReminderRepository.ReminderRow::id).toList())
                .stream()
                .collect(Collectors.toMap(NotificationEntity::getReminderId, Function.identity()));
    List<AppointmentDtos.ReminderItem> items = new ArrayList<>();
    for (ReminderRepository.ReminderRow row : rows) {
      NotificationEntity note = notes.get(row.id());
      items.add(
          new AppointmentDtos.ReminderItem(
              row.offsetMinutes(),
              row.dueAt(),
              row.status(),
              note == null
                  ? AppointmentDtos.NotificationView.notScheduled()
                  : new AppointmentDtos.NotificationView(
                      note.getId(),
                      note.getStatus(),
                      note.getAttempts(),
                      note.getLastError(),
                      note.getSentAt(),
                      note.getNextAttemptAt())));
    }
    return items;
  }

  @Transactional(readOnly = true)
  public PageResponse<AppointmentDtos.AppointmentResponse> list(
      PageQuery query, AppointmentStatus status) {
    AuthPrincipal user = CurrentUser.require();
    var pageable = pages.pageable(query);
    if (user.role() == Role.CUSTOMER) {
      CustomerEntity customer =
          customers.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
      return mapPage(
          appointments.searchCustomer(
              customer.getId(),
              query.like(),
              status != null,
              // PG cannot infer a null appointment_status bind; ignored when hasStatus is
              // false.
              status != null ? status : AppointmentStatus.CONFIRMED,
              pageable),
          user.role(),
          customer,
          null);
    }
    DealershipStaffEntity membership =
        staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
    DealershipEntity shop =
        dealerships.findById(membership.getDealershipId()).orElseThrow(ApiException::notFound);
    return mapPage(
        appointments.searchStaff(
            shop.getId(),
            query.like(),
            status != null,
            status != null ? status : AppointmentStatus.CONFIRMED,
            pageable),
        user.role(),
        null,
        shop);
  }

  private record VisibleRow(
      AppointmentEntity appointment, CustomerEntity customer, DealershipEntity shop) {}

  private AuthPrincipal requireStaff() {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can complete an Appointment");
    }
    return user;
  }

  private VisibleRow loadVisible(UUID id, AuthPrincipal user) {
    if (user.role() == Role.CUSTOMER) {
      CustomerEntity customer =
          customers.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
      AppointmentEntity appointment =
          appointments
              .findByIdAndCustomerId(id, customer.getId())
              .orElseThrow(ApiException::notFound);
      return new VisibleRow(appointment, customer, null);
    }
    DealershipStaffEntity membership =
        staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
    DealershipEntity shop =
        dealerships.findById(membership.getDealershipId()).orElseThrow(ApiException::notFound);
    AppointmentEntity appointment =
        appointments.findByIdAndDealershipId(id, shop.getId()).orElseThrow(ApiException::notFound);
    return new VisibleRow(appointment, null, shop);
  }

  private PageResponse<AppointmentDtos.AppointmentResponse> mapPage(
      Page<AppointmentEntity> page,
      Role viewer,
      CustomerEntity knownCustomer,
      DealershipEntity knownShop) {
    List<AppointmentEntity> rows = page.getContent();
    if (rows.isEmpty()) {
      return new PageResponse<>(
          List.of(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
    // one findAllById per table so a page of 100 is a few IN queries, not 301
    Map<UUID, CustomerEntity> byCustomer =
        knownCustomer != null
            ? Map.of(knownCustomer.getId(), knownCustomer)
            : byId(
                customers.findAllById(
                    rows.stream().map(AppointmentEntity::getCustomerId).distinct().toList()),
                CustomerEntity::getId);
    Map<UUID, VehicleEntity> byVehicle =
        byId(
            vehicles.findAllById(
                rows.stream().map(AppointmentEntity::getVehicleId).distinct().toList()),
            VehicleEntity::getId);
    Map<UUID, DealershipEntity> byShop =
        knownShop != null
            ? Map.of(knownShop.getId(), knownShop)
            : byId(
                dealerships.findAllById(
                    rows.stream().map(AppointmentEntity::getDealershipId).distinct().toList()),
                DealershipEntity::getId);
    Map<UUID, String> names = namesByUserId(byCustomer.values());
    return PageResponse.of(
        page.map(
            a ->
                toResponse(
                    a,
                    require(byCustomer.get(a.getCustomerId())),
                    require(byVehicle.get(a.getVehicleId())),
                    require(byShop.get(a.getDealershipId())),
                    viewer,
                    names.get(require(byCustomer.get(a.getCustomerId())).getUserId()))));
  }

  private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> id) {
    return rows.stream().collect(Collectors.toMap(id, Function.identity()));
  }

  private static <T> T require(T value) {
    if (value == null) {
      throw ApiException.notFound();
    }
    return value;
  }

  private AppointmentDtos.AppointmentResponse toResponse(VisibleRow row, Role viewer) {
    AppointmentEntity appointment = row.appointment();
    CustomerEntity customer =
        row.customer() != null
            ? row.customer()
            : customers.findById(appointment.getCustomerId()).orElseThrow(ApiException::notFound);
    VehicleEntity vehicle =
        vehicles.findById(appointment.getVehicleId()).orElseThrow(ApiException::notFound);
    DealershipEntity shop =
        row.shop() != null
            ? row.shop()
            : dealerships
                .findById(appointment.getDealershipId())
                .orElseThrow(ApiException::notFound);
    return toResponse(appointment, customer, vehicle, shop, viewer, nameOf(customer));
  }

  private AppointmentDtos.AppointmentResponse toResponse(
      AppointmentEntity appointment,
      CustomerEntity customer,
      VehicleEntity vehicle,
      DealershipEntity shop,
      Role viewer,
      String customerName) {
    String local =
        viewer == Role.DEALERSHIP_STAFF
            ? BookingTimes.formatStaffLocal(appointment.getScheduledAt(), shop.getTimezone())
            : BookingTimes.formatOffsetDateTime(
                appointment.getScheduledAt(),
                BookingTimes.parseStoredOffset(appointment.getDisplayOffset()));
    return new AppointmentDtos.AppointmentResponse(
        appointment.getId(),
        appointment.getCustomerId(),
        appointment.getVehicleId(),
        appointment.getDealershipId(),
        CustomerDtos.CustomerSummary.from(customer, customerName),
        VehicleDtos.VehicleResponse.from(vehicle, customer, customerName),
        DealershipDtos.DealershipResponse.from(shop),
        appointment.getScheduledAt(),
        appointment.getDisplayOffset(),
        local,
        appointment.getStatus(),
        appointment.getCreatedByRole(),
        appointment.isNotify());
  }

  private String nameOf(CustomerEntity customer) {
    return users.findById(customer.getUserId()).map(UserEntity::getName).orElse(null);
  }

  private AppointmentDtos.AppointmentResponse closeConfirmed(
      UUID id, AppointmentStatus next, String verb) {
    AuthPrincipal user = CurrentUser.require();
    VisibleRow row = loadVisible(id, user);
    AppointmentEntity appointment = row.appointment();
    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
      throw ApiException.of(
          ApiErrorCode.NOT_CONFIRMED, "Only a Confirmed Appointment can be " + verb);
    }
    appointment.setStatus(next);
    appointments.save(appointment);
    reminders.cancelUnsent(appointment.getId());
    return toResponse(row, user.role());
  }

  private Map<UUID, String> namesByUserId(Iterable<CustomerEntity> rows) {
    List<UUID> userIds = new ArrayList<>();
    for (CustomerEntity row : rows) {
      userIds.add(row.getUserId());
    }
    Map<UUID, String> names = new HashMap<>();
    for (UserEntity user : users.findAllById(userIds)) {
      names.put(user.getId(), user.getName());
    }
    return names;
  }
}
