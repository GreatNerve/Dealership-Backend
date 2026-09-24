package com.dealership.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordedTest {

  @Test
  void dropsOldestPastCap() {
    List<Integer> items = new ArrayList<>();
    for (int i = 0; i < Recorded.MAX + 3; i++) {
      Recorded.add(items, i);
    }
    assertEquals(Recorded.MAX, items.size());
    assertEquals(3, items.get(0));
  }
}
