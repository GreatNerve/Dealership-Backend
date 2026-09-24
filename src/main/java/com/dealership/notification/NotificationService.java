package com.dealership.notification;

import com.dealership.appointment.AppointmentDtos;
import com.dealership.appointment.AppointmentEntity;
import com.dealership.appointment.AppointmentRepository;
import com.dealership.appointment.AppointmentService;
import com.dealership.appointment.IdempotencyService;
import com.dealership.dealership.DealershipEntity;
import com.dealership.dealership.DealershipStaffRepository;
import com.dealership.dealership.HomeDealerships;
import com.dealership.identity.Role;
import com.dealership.notification.webhook.DeliveryWebhookAdapter;
import com.dealership.reminder.ReminderRepository;
import com.dealership.reminder.ReminderRepository.MailFacts;
import com.dealership.shared.access.ResourceAccess;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import com.dealership.shared.api.InstantRange;
import com.dealership.shared.api.PageQueries;
import com.dealership.shared.api.PageQuery;
import com.dealership.shared.api.PageResponse;
import com.dealership.shared.api.StatsBucket;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.db.SqlValues;
import com.dealership.shared.metrics.AppMetrics;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notifications;
  private final NotificationDeliveryEventRepository events;
  private final NotificationLeaseRepository leases;
  private final OutboxEventRepository outboxEvents;
  private final ReminderRepository reminders;
  private final AppointmentRepository appointments;
  // AppointmentService → ReminderService → this
  private final AppointmentService appointmentViews;
  private final HomeDealerships homeDealerships;
  private final DealershipStaffRepository staff;
  private final IdempotencyService idempotency;
  private final AppProperties properties;
  private final PageQueries pages;
  private final AppMetrics metrics;

  public NotificationService(
      NotificationRepository notifications,
      NotificationDeliveryEventRepository events,
      NotificationLeaseRepository leases,
      OutboxEventRepository outboxEvents,
      ReminderRepository reminders,
      AppointmentRepository appointments,
      @Lazy AppointmentService appointmentViews,
      HomeDealerships homeDealerships,
      DealershipStaffRepository staff,
      IdempotencyService idempotency,
      AppProperties properties,
      PageQueries pages,
      AppMetrics metrics) {
    this.notifications = notifications;
    this.events = events;
    this.leases = leases;
    this.outboxEvents = outboxEvents;
    this.reminders = reminders;
    this.appointments = appointments;
    this.appointmentViews = appointmentViews;
    this.homeDealerships = homeDealerships;
    this.staff = staff;
    this.idempotency = idempotency;
    this.properties = properties;
    this.pages = pages;
    this.metrics = metrics;
  }

  @Transactional
  public void enqueueDue(MailFacts facts) {
    NotificationEntity row = ensureSystemNotification(facts);
    outboxEvents.save(
        outbox(
            OutboxEventType.REMINDER_DUE, facts.reminderId(), systemSnapshot(facts, row.getId())));
  }

  @Transactional
  public NotificationDtos.NotificationResponse enqueueManual(
      UUID appointmentId, String idempotencyKey, String subject, String body) {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can send Notifications");
    }
    var membership = staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
    AppointmentEntity appointment =
        appointments.findById(appointmentId).orElseThrow(ApiException::notFound);
    ResourceAccess.requireVisible(
        appointment.getDealershipId().equals(membership.getDealershipId()));
    String cleanSubject = requireText(subject, "subject");
    String cleanBody = requireMultiline(body, "body");
    var begin =
        idempotency.begin(
            user.userId(),
            idempotencyKey,
            idempotency.fingerprint(
                new ManualSendFingerprint(appointmentId, cleanSubject, cleanBody)));
    if (begin.replay()) {
      return idempotency.replay(begin.row(), NotificationDtos.NotificationResponse.class);
    }
    var facts =
        reminders.loadMailFactsForAppointment(appointmentId).orElseThrow(ApiException::notFound);
    NotificationEntity row = new NotificationEntity();
    row.setId(UUID.randomUUID());
    row.setDealershipId(appointment.getDealershipId());
    row.setAppointmentId(appointmentId);
    row.setChannel(NotificationChannel.EMAIL);
    row.setGeneration(NotificationGeneration.MANUAL);
    row.setSubject(cleanSubject);
    row.setBody(cleanBody);
    row.setIdempotencyKey(appointmentId + ":MANUAL:" + row.getId());
    row.setStatus(NotificationStatus.PENDING);
    notifications.saveAndFlush(row);
    outboxEvents.save(
        outbox(OutboxEventType.MANUAL_NOTIFICATION, row.getId(), manualSnapshot(row, facts, 0)));
    var response =
        toListItem(
            row,
            false,
            java.util.Set.of(),
            java.util.Set.of(),
            List.of(),
            appointmentView(appointmentId));
    idempotency.complete(begin.row(), row.getId(), response);
    return response;
  }

  @Transactional
  public void pollManualRetries(String workerId) {
    var claimed =
        leases.claimRetryDue(
            workerId, properties.getWorkers().getLease(), properties.getWorkers().getClaimBatch());
    for (var row : claimed) {
      var facts = reminders.loadMailFactsForAppointment(row.appointmentId()).orElse(null);
      if (facts == null) {
        continue;
      }
      outboxEvents.save(
          outbox(OutboxEventType.MANUAL_NOTIFICATION, row.id(), manualSnapshot(row, facts)));
    }
  }

  @Transactional
  public void replay(UUID notificationId) {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can replay Notifications");
    }
    var membership = staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound);
    NotificationEntity row =
        notifications.findById(notificationId).orElseThrow(ApiException::notFound);
    ResourceAccess.requireVisible(row.getDealershipId().equals(membership.getDealershipId()));
    if (row.getStatus() == NotificationStatus.SENT) {
      throw ApiException.of(ApiErrorCode.ALREADY_SENT, "Notification already sent");
    }
    if (row.getStatus() != NotificationStatus.DEAD_LETTER) {
      throw ApiException.of(
          ApiErrorCode.REPLAY_NOT_DEAD_LETTER, "Only a dead-lettered Notification can be replayed");
    }
    if (row.getGeneration() == NotificationGeneration.SYSTEM) {
      if (!reminders.reopenDead(row.getReminderId(), properties.getWorkers().getLease())) {
        throw ApiException.of(
            ApiErrorCode.REPLAY_NOT_DEAD_LETTER,
            "Only a dead-lettered Notification can be replayed");
      }
      row.markReplay();
      notifications.save(row);
      var facts = reminders.loadMailFacts(row.getReminderId()).orElseThrow(ApiException::notFound);
      enqueueDue(facts);
      return;
    }
    row.markReplay();
    notifications.save(row);
    var facts =
        reminders
            .loadMailFactsForAppointment(row.getAppointmentId())
            .orElseThrow(ApiException::notFound);
    outboxEvents.save(
        outbox(OutboxEventType.MANUAL_NOTIFICATION, row.getId(), manualSnapshot(row, facts, 0)));
  }

  @Transactional(readOnly = true)
  public PageResponse<NotificationDtos.NotificationResponse> list(
      PageQuery query,
      InstantRange range,
      NotificationStatus status,
      NotificationGeneration generation,
      NotificationChannel channel,
      UUID appointmentId,
      DeliveryEventType hasEvent) {
    UUID shopId = homeDealership();
    Instant from = range == null ? null : range.from();
    Instant to = range == null ? null : range.to();
    var page =
        notifications.searchStaff(
            shopId,
            query.like(),
            status != null,
            status != null ? status : NotificationStatus.PENDING,
            generation != null,
            generation != null ? generation : NotificationGeneration.SYSTEM,
            channel != null,
            channel != null ? channel : NotificationChannel.EMAIL,
            appointmentId != null,
            appointmentId != null ? appointmentId : shopId,
            from != null,
            from != null ? from : Instant.EPOCH,
            to != null,
            to != null ? to : Instant.EPOCH,
            hasEvent != null,
            hasEvent != null ? hasEvent : DeliveryEventType.OPENED,
            pages.pageable(query));
    var ids = page.getContent().stream().map(NotificationEntity::getId).toList();
    var flags = eventFlags(ids);
    var byEvents =
        appointmentId != null
            ? timelines(ids)
            : Map.<UUID, List<NotificationDtos.DeliveryEventView>>of();
    var byAppointment =
        appointmentViews.mapByIds(
            page.getContent().stream()
                .map(NotificationEntity::getAppointmentId)
                .distinct()
                .toList(),
            Role.DEALERSHIP_STAFF);
    return PageResponse.of(
        page.map(
            row ->
                toListItem(
                    row,
                    false,
                    flags.opened(),
                    flags.bounced(),
                    byEvents.getOrDefault(row.getId(), List.of()),
                    byAppointment.get(row.getAppointmentId()))));
  }

  @Transactional(readOnly = true)
  public NotificationDtos.NotificationResponse get(UUID id) {
    UUID shopId = homeDealership();
    NotificationEntity row = notifications.findById(id).orElseThrow(ApiException::notFound);
    ResourceAccess.requireVisible(row.getDealershipId().equals(shopId));
    return toDetail(row, appointmentView(row.getAppointmentId()));
  }

  @Transactional(readOnly = true)
  public NotificationDtos.Stats stats(InstantRange range, StatsBucket bucket) {
    return stats(homeDealerships.requireStaffShop(), range, bucket);
  }

  @Transactional(readOnly = true)
  public NotificationDtos.Stats stats(
      DealershipEntity shop, InstantRange range, StatsBucket bucket) {
    UUID shopId = shop.getId();
    ZoneId zone = ZoneId.of(shop.getTimezone());
    if (bucket != null) {
      bucket.requireFit(range.from(), range.to(), zone);
    }
    Object[] totals = notifications.countTotals(shopId, range.from(), range.to()).get(0);
    List<NotificationDtos.DailyStats> buckets = List.of();
    if (bucket != null) {
      Map<LocalDate, long[]> byPeriod = new HashMap<>();
      for (Object[] row :
          notifications.countByBucket(shopId, bucket.unit(), range.from(), range.to())) {
        LocalDate day = SqlValues.localDate(row[0]);
        long[] acc = byPeriod.computeIfAbsent(day, ignored -> new long[4]);
        long n = ((Number) row[2]).longValue();
        switch (String.valueOf(row[1])) {
          case "sent" -> acc[0] += n;
          case "failed" -> acc[1] += n;
          case "bounced" -> acc[2] += n;
          case "opened" -> acc[3] += n;
          default -> {}
        }
      }
      Map<LocalDate, NotificationDtos.DailyStats> found = new HashMap<>();
      for (var e : byPeriod.entrySet()) {
        long[] v = e.getValue();
        found.put(e.getKey(), new NotificationDtos.DailyStats(e.getKey(), v[0], v[1], v[2], v[3]));
      }
      buckets =
          bucket.fill(
              range.from(),
              range.to(),
              zone,
              found,
              day -> new NotificationDtos.DailyStats(day, 0, 0, 0, 0));
    }
    return new NotificationDtos.Stats(
        ((Number) totals[0]).longValue(),
        ((Number) totals[1]).longValue(),
        ((Number) totals[2]).longValue(),
        ((Number) totals[3]).longValue(),
        ((Number) totals[4]).longValue(),
        ((Number) totals[5]).longValue(),
        ((Number) totals[6]).longValue(),
        buckets);
  }

  @Transactional
  public boolean ingestEvents(
      DeliveryProvider provider, List<DeliveryWebhookAdapter.Ingest> batch) {
    if (batch == null || batch.isEmpty()) {
      return false;
    }
    Set<UUID> ids = new HashSet<>();
    for (var ingest : batch) {
      ids.add(ingest.notificationId());
    }
    Set<UUID> known = new HashSet<>();
    for (NotificationEntity row : notifications.findAllById(ids)) {
      known.add(row.getId());
    }
    if (known.isEmpty()) {
      return false;
    }
    Set<String> seen = new HashSet<>();
    for (NotificationDeliveryEventEntity row : events.findByNotificationIdIn(known)) {
      if (row.getProvider() == provider) {
        seen.add(row.getNotificationId() + "\0" + row.getProviderEventId());
      }
    }
    List<NotificationDeliveryEventEntity> rows = new ArrayList<>();
    Instant now = Instant.now();
    for (var ingest : batch) {
      if (!known.contains(ingest.notificationId())) {
        continue;
      }
      String eventId = Inputs.fit(ingest.providerEventId(), 255);
      String key = ingest.notificationId() + "\0" + eventId;
      if (!seen.add(key)) {
        continue;
      }
      NotificationDeliveryEventEntity row = new NotificationDeliveryEventEntity();
      row.setNotificationId(ingest.notificationId());
      row.setProvider(provider);
      row.setProviderEventId(eventId);
      row.setEventType(ingest.type());
      row.setOccurredAt(ingest.occurredAt() == null ? now : ingest.occurredAt());
      row.setRawType(Inputs.clip(ingest.rawType(), 64));
      rows.add(row);
    }
    if (!rows.isEmpty()) {
      events.saveAll(rows);
      metrics.deliveryEventsIngested(rows.size());
    }
    return true;
  }

  @Transactional(readOnly = true)
  public boolean alreadySent(String idempotencyKey) {
    return notifications.existsByIdempotencyKeyAndStatus(idempotencyKey, NotificationStatus.SENT);
  }

  public boolean heartbeatManual(UUID notificationId, String workerId, Duration lease) {
    return leases.heartbeat(notificationId, workerId, lease);
  }

  public boolean markSentManual(UUID notificationId, String workerId) {
    return leases.markSent(notificationId, workerId);
  }

  public boolean markRetryManual(UUID notificationId, String workerId, Instant next, String error) {
    return leases.markRetry(notificationId, workerId, next, error);
  }

  public boolean markDeadManual(UUID notificationId, String workerId, String error) {
    return leases.markDead(notificationId, workerId, error);
  }

  public Map<String, String> correlationHeaders(MailSnapshot snapshot) {
    if (snapshot.notificationId() == null) {
      return Map.of();
    }
    String header = properties.getNotifications().getCorrelationHeader();
    if (header == null || header.isBlank()) {
      return Map.of();
    }
    return Map.of(header, snapshot.notificationId().toString());
  }

  @Transactional
  public void markSent(String idempotencyKey) {
    notifications.findByIdempotencyKey(idempotencyKey).ifPresent(NotificationEntity::markSent);
  }

  @Transactional
  public void markRetry(String idempotencyKey, Instant next, String error) {
    notifications.findByIdempotencyKey(idempotencyKey).ifPresent(row -> row.markRetry(next, error));
  }

  @Transactional
  public void markDead(String idempotencyKey, String error) {
    notifications.findByIdempotencyKey(idempotencyKey).ifPresent(row -> row.markDead(error));
  }

  @Transactional
  public void markPublished(UUID outboxId) {
    outboxEvents.findById(outboxId).ifPresent(OutboxEventEntity::markPublished);
  }

  private NotificationEntity ensureSystemNotification(MailFacts facts) {
    var existing = notifications.findByIdempotencyKey(facts.idempotencyKey());
    if (existing.isPresent()) {
      return existing.get();
    }
    NotificationEntity row = new NotificationEntity();
    row.setDealershipId(facts.dealershipId());
    row.setAppointmentId(facts.appointmentId());
    row.setReminderId(facts.reminderId());
    row.setOffsetMinutes(facts.offsetMinutes());
    row.setChannel(NotificationChannel.EMAIL);
    row.setGeneration(NotificationGeneration.SYSTEM);
    row.setIdempotencyKey(facts.idempotencyKey());
    row.setStatus(NotificationStatus.PENDING);
    try {
      return notifications.saveAndFlush(row);
    } catch (DataIntegrityViolationException ignored) {
      return notifications.findByIdempotencyKey(facts.idempotencyKey()).orElseThrow();
    }
  }

  private UUID homeDealership() {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can read Notifications");
    }
    return staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound).getDealershipId();
  }

  private record EventFlags(java.util.Set<UUID> opened, java.util.Set<UUID> bounced) {}

  private EventFlags eventFlags(List<UUID> ids) {
    if (ids.isEmpty()) {
      return new EventFlags(java.util.Set.of(), java.util.Set.of());
    }
    java.util.Set<UUID> opened = new java.util.HashSet<>();
    java.util.Set<UUID> bounced = new java.util.HashSet<>();
    for (var flag :
        events.findDistinctFlags(
            ids,
            List.of(
                DeliveryEventType.OPENED,
                DeliveryEventType.SOFT_BOUNCE,
                DeliveryEventType.HARD_BOUNCE,
                DeliveryEventType.BLOCKED))) {
      if (flag.getEventType() == DeliveryEventType.OPENED) {
        opened.add(flag.getNotificationId());
      } else if (DeliveryEventType.bounce(flag.getEventType())) {
        bounced.add(flag.getNotificationId());
      }
    }
    return new EventFlags(opened, bounced);
  }

  private AppointmentDtos.AppointmentResponse appointmentView(UUID appointmentId) {
    return appointmentViews
        .mapByIds(List.of(appointmentId), Role.DEALERSHIP_STAFF)
        .get(appointmentId);
  }

  private NotificationDtos.NotificationResponse toListItem(
      NotificationEntity row,
      boolean includeBody,
      java.util.Set<UUID> openedIds,
      java.util.Set<UUID> bouncedIds,
      List<NotificationDtos.DeliveryEventView> timeline,
      AppointmentDtos.AppointmentResponse appointment) {
    boolean opened =
        openedIds.contains(row.getId())
            || timeline.stream().anyMatch(e -> e.eventType() == DeliveryEventType.OPENED);
    boolean bounced =
        bouncedIds.contains(row.getId())
            || timeline.stream().anyMatch(e -> DeliveryEventType.bounce(e.eventType()));
    return new NotificationDtos.NotificationResponse(
        row.getId(),
        row.getDealershipId(),
        row.getAppointmentId(),
        row.getReminderId(),
        row.getOffsetMinutes(),
        row.getChannel(),
        row.getGeneration(),
        row.getStatus(),
        row.getAttempts(),
        row.getLastError(),
        row.getSentAt(),
        row.getNextAttemptAt(),
        includeBody ? row.getSubject() : null,
        includeBody ? row.getBody() : null,
        opened,
        bounced,
        timeline,
        appointment);
  }

  private NotificationDtos.NotificationResponse toDetail(
      NotificationEntity row, AppointmentDtos.AppointmentResponse appointment) {
    var timeline = timelines(List.of(row.getId())).getOrDefault(row.getId(), List.of());
    boolean opened = timeline.stream().anyMatch(e -> e.eventType() == DeliveryEventType.OPENED);
    boolean bounced = timeline.stream().anyMatch(e -> DeliveryEventType.bounce(e.eventType()));
    return new NotificationDtos.NotificationResponse(
        row.getId(),
        row.getDealershipId(),
        row.getAppointmentId(),
        row.getReminderId(),
        row.getOffsetMinutes(),
        row.getChannel(),
        row.getGeneration(),
        row.getStatus(),
        row.getAttempts(),
        row.getLastError(),
        row.getSentAt(),
        row.getNextAttemptAt(),
        row.getGeneration() == NotificationGeneration.MANUAL ? row.getSubject() : null,
        row.getGeneration() == NotificationGeneration.MANUAL ? row.getBody() : null,
        opened,
        bounced,
        timeline,
        appointment);
  }

  private Map<UUID, List<NotificationDtos.DeliveryEventView>> timelines(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<NotificationDtos.DeliveryEventView>> out = new HashMap<>();
    for (NotificationDeliveryEventEntity row : events.findByNotificationIdIn(ids)) {
      out.computeIfAbsent(row.getNotificationId(), ignored -> new ArrayList<>())
          .add(
              new NotificationDtos.DeliveryEventView(
                  row.getEventType(), deliveryOccurredAt(row.getOccurredAt()), row.getProvider()));
    }
    for (List<NotificationDtos.DeliveryEventView> list : out.values()) {
      list.sort(Comparator.comparing(NotificationDtos.DeliveryEventView::occurredAt).reversed());
    }
    return out;
  }

  // Rows ingested before V912 stored Brevo ts_epoch as seconds (year ~58699).
  private static Instant deliveryOccurredAt(Instant at) {
    long sec = at.getEpochSecond();
    return sec >= 1_000_000_000_000L ? Instant.ofEpochMilli(sec) : at;
  }

  private static String requireText(String raw, String field) {
    return requireCleaned(Inputs.sanitize(raw), field);
  }

  private static String requireMultiline(String raw, String field) {
    return requireCleaned(Inputs.multiline(raw), field);
  }

  private static String requireCleaned(String cleaned, String field) {
    if (cleaned == null || cleaned.isBlank()) {
      throw ApiException.of(ApiErrorCode.VALIDATION_ERROR, field + " is required");
    }
    return cleaned;
  }

  private MailSnapshot systemSnapshot(MailFacts facts, UUID notificationId) {
    return new MailSnapshot(
        facts.reminderId(),
        notificationId,
        facts.appointmentId(),
        facts.dealershipId(),
        facts.offsetMinutes(),
        facts.scheduleVersion(),
        facts.scheduledAt(),
        facts.displayOffset(),
        facts.dealershipName(),
        facts.customerName(),
        facts.vehicleMake(),
        facts.vehicleModel(),
        facts.vehicleYear(),
        facts.registrationNumber(),
        facts.contact(),
        facts.idempotencyKey(),
        facts.notifyEnabled(),
        facts.attempts(),
        NotificationGeneration.SYSTEM,
        null,
        null);
  }

  private MailSnapshot manualSnapshot(NotificationEntity row, MailFacts facts, int attempts) {
    return manualSnapshot(
        row.getId(),
        row.getAppointmentId(),
        row.getDealershipId(),
        row.getIdempotencyKey(),
        row.getSubject(),
        row.getBody(),
        attempts,
        facts);
  }

  private MailSnapshot manualSnapshot(
      NotificationLeaseRepository.ClaimedManual row, MailFacts facts) {
    return manualSnapshot(
        row.id(),
        row.appointmentId(),
        row.dealershipId(),
        row.idempotencyKey(),
        row.subject(),
        row.body(),
        row.attempts(),
        facts);
  }

  private MailSnapshot manualSnapshot(
      UUID notificationId,
      UUID appointmentId,
      UUID dealershipId,
      String idempotencyKey,
      String subject,
      String body,
      int attempts,
      MailFacts facts) {
    return new MailSnapshot(
        null,
        notificationId,
        appointmentId,
        dealershipId,
        null,
        null,
        facts.scheduledAt(),
        facts.displayOffset(),
        facts.dealershipName(),
        facts.customerName(),
        facts.vehicleMake(),
        facts.vehicleModel(),
        facts.vehicleYear(),
        facts.registrationNumber(),
        facts.contact(),
        idempotencyKey,
        true,
        attempts,
        NotificationGeneration.MANUAL,
        subject,
        body);
  }

  private OutboxEventEntity outbox(OutboxEventType type, UUID aggregateId, MailSnapshot snapshot) {
    OutboxEventEntity event = new OutboxEventEntity();
    event.setEventType(type);
    event.setAggregateId(aggregateId);
    event.setPayload(snapshot);
    event.setStatus(OutboxStatus.PENDING);
    return event;
  }

  private record ManualSendFingerprint(UUID appointmentId, String subject, String body) {}
}
