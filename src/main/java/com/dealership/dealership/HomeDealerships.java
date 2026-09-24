package com.dealership.dealership;

import com.dealership.identity.Role;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

@Component
public class HomeDealerships {

  private final DealershipRepository dealerships;
  private final DealershipStaffRepository staff;

  public HomeDealerships(DealershipRepository dealerships, DealershipStaffRepository staff) {
    this.dealerships = dealerships;
    this.staff = staff;
  }

  public DealershipEntity requireStaffShop() {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can read home Dealership data");
    }
    return dealerships
        .findById(
            staff.findByUserId(user.userId()).orElseThrow(ApiException::notFound).getDealershipId())
        .orElseThrow(ApiException::notFound);
  }
}
