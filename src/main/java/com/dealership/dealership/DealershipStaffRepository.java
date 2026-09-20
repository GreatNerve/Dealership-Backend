package com.dealership.dealership;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealershipStaffRepository extends JpaRepository<DealershipStaffEntity, UUID> {

  Optional<DealershipStaffEntity> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);
}
