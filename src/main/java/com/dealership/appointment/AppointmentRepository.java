package com.dealership.appointment;

import java.time.Instant;
import java.util.List;
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
      value =
          """
          SELECT a.status::text, count(*)
          FROM appointments a
          WHERE a.dealership_id = :shop
            AND a.scheduled_at >= :fromTs AND a.scheduled_at < :toTs
          GROUP BY a.status
          """,
      nativeQuery = true)
  List<Object[]> countByStatus(
      @Param("shop") UUID shop, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

  @Query(
      value =
          """
          SELECT date_trunc(CAST(:bucketUnit AS text), a.scheduled_at AT TIME ZONE d.timezone)::date
                   AS period,
                 a.status::text,
                 count(*)
          FROM appointments a
          JOIN dealerships d ON d.id = a.dealership_id
          WHERE a.dealership_id = :shop
            AND a.scheduled_at >= :fromTs AND a.scheduled_at < :toTs
          GROUP BY 1, 2
          """,
      nativeQuery = true)
  List<Object[]> countByStatusBucket(
      @Param("shop") UUID shop,
      @Param("bucketUnit") String bucketUnit,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs);

  @Query(
      """
      SELECT a FROM AppointmentEntity a
      JOIN VehicleEntity v ON v.id = a.vehicleId AND v.customerId = :customerId
      JOIN DealershipEntity d ON d.id = a.dealershipId
      WHERE a.customerId = :customerId
        AND (:hasStatus = false OR a.status = :status)
        AND (:hasFrom = false OR a.scheduledAt >= :fromTs)
        AND (:hasTo = false OR a.scheduledAt < :toTs)
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
      @Param("hasFrom") boolean hasFrom,
      @Param("fromTs") Instant fromTs,
      @Param("hasTo") boolean hasTo,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  @Query(
      """
      SELECT a FROM AppointmentEntity a
      JOIN VehicleEntity v ON v.id = a.vehicleId
      JOIN DealershipEntity d ON d.id = a.dealershipId
      WHERE a.dealershipId = :dealershipId
        AND (:hasStatus = false OR a.status = :status)
        AND (:hasFrom = false OR a.scheduledAt >= :fromTs)
        AND (:hasTo = false OR a.scheduledAt < :toTs)
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
      @Param("hasFrom") boolean hasFrom,
      @Param("fromTs") Instant fromTs,
      @Param("hasTo") boolean hasTo,
      @Param("toTs") Instant toTs,
      Pageable pageable);
}
