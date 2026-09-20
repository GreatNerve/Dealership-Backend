package com.dealership.shared.security;

import com.dealership.identity.Role;
import com.dealership.shared.api.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

  private CurrentUser() {}

  public static AuthPrincipal require() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
      throw ApiException.unauthorized("Authentication required");
    }
    return principal;
  }

  public static boolean isStaff() {
    return require().role() == Role.DEALERSHIP_STAFF;
  }

  public static boolean isCustomer() {
    return require().role() == Role.CUSTOMER;
  }
}
