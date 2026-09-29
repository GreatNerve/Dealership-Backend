package com.dealership.dealership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealershipCapacityOverrideRepository
    extends JpaRepository<DealershipCapacityOverrideEntity, UUID> {

  List<DealershipCapacityOverrideEntity> findByDealershipIdOrderByFromDateAsc(UUID dealershipId);

  Optional<DealershipCapacityOverrideEntity> findByIdAndDealershipId(UUID id, UUID dealershipId);
}
