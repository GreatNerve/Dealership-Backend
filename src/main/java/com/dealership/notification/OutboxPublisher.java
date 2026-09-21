package com.dealership.notification;

import com.dealership.shared.config.AppProperties;
import com.dealership.shared.config.RabbitConfig;
import com.dealership.shared.metrics.AppMetrics;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRepository outbox;
  private final NotificationService notifications;
  private final RabbitTemplate rabbit;
  private final AppProperties properties;
  private final AppMetrics metrics;
  private final String workerId = "outbox-" + UUID.randomUUID();

  public OutboxPublisher(
      OutboxRepository outbox,
      NotificationService notifications,
      RabbitTemplate rabbit,
      AppProperties properties,
      AppMetrics metrics) {
    this.outbox = outbox;
    this.notifications = notifications;
    this.rabbit = rabbit;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${app.workers.poll-interval}")
  public void drain() {
    MDC.put("worker_id", workerId);
    try {
      for (var claimed :
          outbox.claim(
              workerId,
              properties.getWorkers().getLease(),
              properties.getWorkers().getClaimBatch())) {
        rabbit.convertAndSend(RabbitConfig.NOTIFICATION_QUEUE, claimed.snapshot());
        notifications.markPublished(claimed.id());
        metrics.outboxPublished();
        log.info("outbox published reminder_id={}", claimed.snapshot().reminderId());
      }
    } finally {
      MDC.remove("worker_id");
    }
  }
}
