package com.dealership.appointment;

import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.time.TimeProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

  private final IdempotencyKeyRepository keys;
  private final AppProperties properties;
  private final TimeProvider time;
  private final ObjectMapper mapper;

  public IdempotencyService(
      IdempotencyKeyRepository keys,
      AppProperties properties,
      TimeProvider time,
      ObjectMapper mapper) {
    this.keys = keys;
    this.properties = properties;
    this.time = time;
    this.mapper = mapper;
  }

  public String fingerprint(Object body) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] json = mapper.writeValueAsBytes(body);
      return HexFormat.of().formatHex(digest.digest(json));
    } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record Begin(IdempotencyKeyEntity row, boolean replay) {}

  @Transactional
  public Begin begin(UUID userId, String key, String fingerprint) {
    if (key != null) {
      key = Inputs.sanitize(key);
    }
    if (key == null || key.isBlank() || key.length() > 255) {
      throw ApiException.of(
          ApiErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key is required (max 255)");
    }
    var existing = keys.findByUserIdAndKey(userId, key);
    if (existing.isPresent()) {
      IdempotencyKeyEntity row = existing.get();
      if (row.getExpiresAt().isBefore(time.now())) {
        keys.delete(row);
        keys.flush();
      } else {
        if (!row.getFingerprint().equals(fingerprint)) {
          throw ApiException.of(
              ApiErrorCode.IDEMPOTENCY_KEY_REUSED,
              "Idempotency-Key was used with a different body");
        }
        if (row.getStatus() == IdempotencyStatus.COMPLETED) {
          return new Begin(row, true);
        }
        throw ApiException.of(
            ApiErrorCode.IDEMPOTENCY_IN_FLIGHT, "This Idempotency-Key is already in progress");
      }
    }
    IdempotencyKeyEntity row = new IdempotencyKeyEntity();
    row.setUserId(userId);
    row.setKey(key);
    row.setFingerprint(fingerprint);
    row.setStatus(IdempotencyStatus.STARTED);
    row.setExpiresAt(time.now().plus(properties.getIdempotency().getTtl()));
    try {
      keys.saveAndFlush(row);
    } catch (DataIntegrityViolationException ex) {
      IdempotencyKeyEntity raced = keys.findByUserIdAndKey(userId, key).orElseThrow();
      if (!raced.getFingerprint().equals(fingerprint)) {
        throw ApiException.of(
            ApiErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was used with a different body");
      }
      if (raced.getStatus() == IdempotencyStatus.COMPLETED) {
        return new Begin(raced, true);
      }
      throw ApiException.of(
          ApiErrorCode.IDEMPOTENCY_IN_FLIGHT, "This Idempotency-Key is already in progress");
    }
    return new Begin(row, false);
  }

  @Transactional
  public void complete(IdempotencyKeyEntity row, java.util.UUID resourceId, Object response) {
    try {
      row.setResourceId(resourceId);
      row.setStatus(IdempotencyStatus.COMPLETED);
      row.setResponse(mapper.writeValueAsString(response));
      keys.save(row);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Transactional
  public void purgeExpired() {
    keys.deleteByExpiresAtBefore(time.now());
  }

  public AppointmentDtos.AppointmentResponse replayBody(IdempotencyKeyEntity row) {
    try {
      return mapper.readValue(row.getResponse(), AppointmentDtos.AppointmentResponse.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
