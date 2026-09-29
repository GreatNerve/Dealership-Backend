package com.dealership.dealership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "dealership_hours")
public class DealershipHoursEntity {

  @Id private UUID id;

  @Column(name = "dealership_id", nullable = false)
  private UUID dealershipId;

  @Column(nullable = false)
  private int weekday;

  @Column(nullable = false)
  private boolean closed;

  @Column(name = "open_time")
  private LocalTime openTime;

  @Column(name = "close_time")
  private LocalTime closeTime;

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

  public UUID getDealershipId() {
    return dealershipId;
  }

  public void setDealershipId(UUID dealershipId) {
    this.dealershipId = dealershipId;
  }

  public int getWeekday() {
    return weekday;
  }

  public void setWeekday(int weekday) {
    this.weekday = weekday;
  }

  public boolean isClosed() {
    return closed;
  }

  public void setClosed(boolean closed) {
    this.closed = closed;
  }

  public LocalTime getOpenTime() {
    return openTime;
  }

  public void setOpenTime(LocalTime openTime) {
    this.openTime = openTime;
  }

  public LocalTime getCloseTime() {
    return closeTime;
  }

  public void setCloseTime(LocalTime closeTime) {
    this.closeTime = closeTime;
  }
}
