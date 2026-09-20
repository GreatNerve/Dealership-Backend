# Customer (PRD)

## Stories

1. As a Customer, I want a profile with contact details, so that Reminders have a destination.
2. As a Staff Member, I want to book for an existing Customer, so that phone/walk-in bookings work.

## Rules

- A Customer owns one or more Vehicles.
- No Customer timezone on register or profile. Offset travels with `scheduledAt`.
- v1 has no Staff “create Customer” API. Demo seed provides a Customer for Staff booking. Optional later.
- Privacy (who may search or read whose contact) is later. See [scope.md](scope.md).
