package com.dealership.shared.access;

import com.dealership.shared.api.ApiException;

public final class ResourceAccess {

  private ResourceAccess() {}

  public static void requireVisible(boolean visible) {
    if (!visible) {
      throw ApiException.notFound();
    }
  }
}
