package com.dealership.appointment;

import com.dealership.identity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "appointments")
public class AppointmentEntity {

  @Id private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "vehicle_id", nullable = false)
  private UUID vehicleId;

  @Column(name = "dealership_id", nullable = false)
  private UUID dealershipId;

  @Column(name = "scheduled_at", nullable = false)
  private Instant scheduledAt;

  @Column(name = "display_offset", nullable = false)
  private String displayOffset;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "appointment_status")
  private AppointmentStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "created_by_role", nullable = false, columnDefinition = "user_role")
  private Role createdByRole;

  @Column(nullable = false)
  private boolean notify = true;

  // false is excluded from UNIQUE (vehicle_id) WHERE CONFIRMED so the env toggle can allow two
  @Column(name = "one_confirmed", nullable = false)
  private boolean oneConfirmed = true;

  @Version private long version;

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

  public UUID getCustomerId() {
    return customerId;
  }

  public void setCustomerId(UUID customerId) {
    this.customerId = customerId;
  }

  public UUID getVehicleId() {
    return vehicleId;
  }

  public void setVehicleId(UUID vehicleId) {
    this.vehicleId = vehicleId;
  }

  public UUID getDealershipId() {
    return dealershipId;
  }

  public void setDealershipId(UUID dealershipId) {
    this.dealershipId = dealershipId;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public void setScheduledAt(Instant scheduledAt) {
    this.scheduledAt = scheduledAt;
  }

  public String getDisplayOffset() {
    return displayOffset;
  }

  public void setDisplayOffset(String displayOffset) {
    this.displayOffset = displayOffset;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void setStatus(AppointmentStatus status) {
    this.status = status;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(UUID createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public Role getCreatedByRole() {
    return createdByRole;
  }

  public void setCreatedByRole(Role createdByRole) {
    this.createdByRole = createdByRole;
  }

  public boolean isNotify() {
    return notify;
  }

  public void setNotify(boolean notify) {
    this.notify = notify;
  }

  public boolean isOneConfirmed() {
    return oneConfirmed;
  }

  public void setOneConfirmed(boolean oneConfirmed) {
    this.oneConfirmed = oneConfirmed;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
