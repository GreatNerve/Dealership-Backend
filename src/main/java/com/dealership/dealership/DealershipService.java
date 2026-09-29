package com.dealership.dealership;

import com.dealership.dealership.DealershipDtos.DealershipResponse;
import com.dealership.identity.Role;
import com.dealership.shared.api.ApiErrorCode;
import com.dealership.shared.api.ApiException;
import com.dealership.shared.api.Inputs;
import com.dealership.shared.config.AppProperties;
import com.dealership.shared.security.AuthPrincipal;
import com.dealership.shared.security.CurrentUser;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealershipService {

  private final DealershipRepository dealerships;
  private final DealershipStaffRepository staff;
  private final ServiceSlotService slots;
  private final AppProperties properties;

  public DealershipService(
      DealershipRepository dealerships,
      DealershipStaffRepository staff,
      ServiceSlotService slots,
      AppProperties properties) {
    this.dealerships = dealerships;
    this.staff = staff;
    this.slots = slots;
    this.properties = properties;
  }

  @Transactional
  public DealershipResponse create(String name, String timezone, String address) {
    AuthPrincipal user = CurrentUser.require();
    if (user.role() != Role.DEALERSHIP_STAFF) {
      throw ApiException.forbidden("Only staff can create a Dealership");
    }
    if (staff.existsByUserId(user.userId())) {
      throw ApiException.of(
          ApiErrorCode.HOME_DEALERSHIP_EXISTS, "Staff Member already has a home Dealership");
    }
    String zone = Inputs.sanitize(timezone);
    try {
      ZoneId.of(zone);
    } catch (DateTimeException ex) {
      throw ApiException.of(ApiErrorCode.INVALID_TIMEZONE, "timezone must be an IANA id");
    }
    DealershipEntity shop = new DealershipEntity();
    shop.setName(Inputs.sanitize(name));
    shop.setTimezone(zone);
    shop.setAddress(Inputs.sanitize(address));
    shop.setDefaultCapacity(properties.getAppointments().getDefaultCapacity());
    dealerships.save(shop);
    slots.seedOnCreate(shop);
    DealershipStaffEntity membership = new DealershipStaffEntity();
    membership.setUserId(user.userId());
    membership.setDealershipId(shop.getId());
    staff.save(membership);
    return DealershipResponse.from(shop);
  }
}
