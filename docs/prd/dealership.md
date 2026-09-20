# Dealership (PRD)

## Stories

1. As a Staff Member, I want to create a Dealership and become its Staff Member, so that the shop can book.
2. As a Staff Member, I must not book another Dealership, so that another Venue is always the Customer’s own booking.
3. As a Customer, I want to pick a Dealership when I book, so that I choose the Venue.
4. As a caller, I want a paginated, searchable list of Dealerships.

## Rules

- Creating a Dealership attaches the creator as Staff Member of that shop (home Dealership).
- Dealership has an IANA **Dealership Timezone**. Staff GET of Appointments uses it. Mail uses **Booking Offset** from `scheduledAt`, not this zone.
- One home Dealership per Staff Member in v1.
- No Dealership volume cap. Abuse control is one Confirmed Appointment per Vehicle.
- Staff invites and extra roles (admin vs advisor) are later.
