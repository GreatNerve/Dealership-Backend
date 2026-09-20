package com.dealership.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {
  // JPA cannot claim FOR UPDATE SKIP LOCKED without loading the queue into the JVM.

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public OutboxRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Optional<ClaimedOutbox> claim(String workerId, Duration lease) {
    List<ClaimedOutbox> rows =
        jdbc.query(
            """
            UPDATE outbox_events
            SET status = CAST(:processing AS outbox_status),
                locked_by = :worker,
                locked_at = now(),
                lease_expires_at = now() + CAST(:lease AS interval),
                updated_at = now()
            WHERE id = (
              SELECT o.id FROM outbox_events o
              WHERE o.status IN (CAST(:pending AS outbox_status), CAST(:retry AS outbox_status))
                AND (o.lease_expires_at IS NULL OR o.lease_expires_at < now())
              ORDER BY o.created_at
              FOR UPDATE SKIP LOCKED
              LIMIT 1
            )
            RETURNING id, payload
            """,
            new MapSqlParameterSource()
                .addValue("worker", workerId)
                .addValue("lease", lease.toSeconds() + " seconds")
                .addValue("processing", OutboxStatus.PROCESSING.name())
                .addValue("pending", OutboxStatus.PENDING.name())
                .addValue("retry", OutboxStatus.RETRY_SCHEDULED.name()),
            (rs, i) ->
                new ClaimedOutbox(rs.getObject("id", UUID.class), parse(rs.getString("payload"))));
    return rows.stream().findFirst();
  }

  private MailSnapshot parse(String json) {
    try {
      return mapper.readValue(json, MailSnapshot.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record ClaimedOutbox(UUID id, MailSnapshot snapshot) {}
}
