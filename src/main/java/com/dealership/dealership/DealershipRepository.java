package com.dealership.dealership;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DealershipRepository
    extends JpaRepository<DealershipEntity, UUID>, JpaSpecificationExecutor<DealershipEntity> {

  static Specification<DealershipEntity> matching(String like) {
    return (root, query, cb) -> {
      if (like == null) {
        return cb.conjunction();
      }
      return cb.or(
          cb.like(cb.lower(root.get("name")), like, '\\'),
          cb.like(cb.lower(root.get("address")), like, '\\'));
    };
  }
}
