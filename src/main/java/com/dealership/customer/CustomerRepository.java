package com.dealership.customer;

import com.dealership.vehicle.VehicleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository
    extends JpaRepository<CustomerEntity, UUID>, JpaSpecificationExecutor<CustomerEntity> {

  Optional<CustomerEntity> findByUserId(UUID userId);

  static Specification<CustomerEntity> matching(String like) {
    return (root, query, cb) -> {
      if (like == null) {
        return cb.conjunction();
      }
      var owned = query.subquery(Integer.class);
      var vehicle = owned.from(VehicleEntity.class);
      owned.select(cb.literal(1));
      owned.where(
          cb.equal(vehicle.get("customerId"), root.get("id")),
          cb.or(
              cb.like(cb.lower(vehicle.get("registrationNumber")), like, '\\'),
              cb.like(cb.lower(vehicle.get("make")), like, '\\'),
              cb.like(cb.lower(vehicle.get("model")), like, '\\')));
      return cb.or(cb.like(cb.lower(root.get("contact")), like, '\\'), cb.exists(owned));
    };
  }
}
