package com.dealership.appointment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

  Optional<IdempotencyKeyEntity> findByUserIdAndKey(UUID userId, String key);

  @Transactional
  long deleteByExpiresAtBefore(Instant cutoff);
}
