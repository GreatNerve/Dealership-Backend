package com.dealership.notification;

import java.util.List;

public final class Recorded {

  static final int MAX = 256;

  private Recorded() {}

  public static <T> void add(List<T> items, T item) {
    items.add(item);
    while (items.size() > MAX) {
      items.remove(0);
    }
  }
}
