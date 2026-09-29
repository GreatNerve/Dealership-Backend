package com.dealership.dealership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "dealership_capacity_overrides")
public class DealershipCapacityOverrideEntity {

  @Id private UUID id;

  @Column(name = "dealership_id", nullable = false)
  private UUID dealershipId;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Column(name = "from_time")
  private LocalTime fromTime;

  @Column(name = "to_time")
  private LocalTime toTime;

  @Column(nullable = false)
  private int capacity;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getDealershipId() {
    return dealershipId;
  }

  public void setDealershipId(UUID dealershipId) {
    this.dealershipId = dealershipId;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public void setFromDate(LocalDate fromDate) {
    this.fromDate = fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public void setToDate(LocalDate toDate) {
    this.toDate = toDate;
  }

  public LocalTime getFromTime() {
    return fromTime;
  }

  public void setFromTime(LocalTime fromTime) {
    this.fromTime = fromTime;
  }

  public LocalTime getToTime() {
    return toTime;
  }

  public void setToTime(LocalTime toTime) {
    this.toTime = toTime;
  }

  public int getCapacity() {
    return capacity;
  }

  public void setCapacity(int capacity) {
    this.capacity = capacity;
  }
}
