package com.dealership.vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<VehicleEntity, UUID> {

  boolean existsByRegistrationNumber(String registrationNumber);

  Optional<VehicleEntity> findByRegistrationNumber(String registrationNumber);

  Optional<VehicleEntity> findByIdAndCustomerId(UUID id, UUID customerId);

  List<VehicleEntity> findByCustomerIdIn(Collection<UUID> customerIds);

  @Query(
      """
      SELECT v FROM VehicleEntity v
      WHERE v.customerId = :customerId
        AND (:q IS NULL
          OR lower(v.registrationNumber) LIKE :q ESCAPE '\\'
          OR lower(v.make) LIKE :q ESCAPE '\\'
          OR lower(v.model) LIKE :q ESCAPE '\\')
      """)
  Page<VehicleEntity> searchOwn(
      @Param("customerId") UUID customerId, @Param("q") String q, Pageable pageable);
}
