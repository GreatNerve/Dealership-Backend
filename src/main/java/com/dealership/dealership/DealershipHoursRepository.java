package com.dealership.dealership;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealershipHoursRepository extends JpaRepository<DealershipHoursEntity, UUID> {

  List<DealershipHoursEntity> findByDealershipIdOrderByWeekdayAsc(UUID dealershipId);

  void deleteByDealershipId(UUID dealershipId);
}
