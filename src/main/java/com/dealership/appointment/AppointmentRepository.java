package com.dealership.appointment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {

  Optional<AppointmentEntity> findByIdAndCustomerId(UUID id, UUID customerId);

  Optional<AppointmentEntity> findByIdAndDealershipId(UUID id, UUID dealershipId);

  @Query(
      """
      SELECT a FROM AppointmentEntity a
      JOIN VehicleEntity v ON v.id = a.vehicleId AND v.customerId = :customerId
      JOIN DealershipEntity d ON d.id = a.dealershipId
      WHERE a.customerId = :customerId
        AND (:hasStatus = false OR a.status = :status)
        AND (:q IS NULL
          OR lower(v.registrationNumber) LIKE :q ESCAPE '\\'
          OR lower(v.make) LIKE :q ESCAPE '\\'
          OR lower(v.model) LIKE :q ESCAPE '\\'
          OR lower(cast(a.status as string)) LIKE :q ESCAPE '\\'
          OR lower(d.name) LIKE :q ESCAPE '\\')
      """)
  Page<AppointmentEntity> searchCustomer(
      @Param("customerId") UUID customerId,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") AppointmentStatus status,
      Pageable pageable);

  @Query(
      """
      SELECT a FROM AppointmentEntity a
      JOIN VehicleEntity v ON v.id = a.vehicleId
      JOIN DealershipEntity d ON d.id = a.dealershipId
      WHERE a.dealershipId = :dealershipId
        AND (:hasStatus = false OR a.status = :status)
        AND (:q IS NULL
          OR lower(v.registrationNumber) LIKE :q ESCAPE '\\'
          OR lower(v.make) LIKE :q ESCAPE '\\'
          OR lower(v.model) LIKE :q ESCAPE '\\'
          OR lower(cast(a.status as string)) LIKE :q ESCAPE '\\'
          OR lower(d.name) LIKE :q ESCAPE '\\')
      """)
  Page<AppointmentEntity> searchStaff(
      @Param("dealershipId") UUID dealershipId,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") AppointmentStatus status,
      Pageable pageable);
}
