package com.dealership.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HardwareSizingTest {

  @Test
  void claimBatchUsesAssignmentTenXFloorWhenCpusAreLower() {
    assertEquals(18, HardwareSizing.claimBatch(4));
    assertEquals(18, HardwareSizing.claimBatch(16));
    assertEquals(32, HardwareSizing.claimBatch(32));
    assertEquals(50, HardwareSizing.claimBatch(64));
  }

  @Test
  void hikariIsTwoTimesCpusWithinEightAndThirtyTwo() {
    assertEquals(8, HardwareSizing.hikariPool(2));
    assertEquals(32, HardwareSizing.hikariPool(16));
    assertEquals(32, HardwareSizing.hikariPool(32));
  }

  @Test
  void tomcatIsSixteenTimesCpusCappedAtTwoHundred() {
    assertEquals(64, HardwareSizing.tomcatMax(4));
    assertEquals(200, HardwareSizing.tomcatMax(16));
  }
}
