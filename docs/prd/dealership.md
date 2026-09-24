# Dealership (PRD)

## Stories

1. As a Staff Member, I want to create a Dealership and become its Staff Member, so that the shop can book.
2. As a Staff Member, I must not book another Dealership, so that another Venue is always the Customer’s own booking.
3. As a Customer, I want to pick a Dealership when I book, so that I choose the Venue.
4. As a caller, I want a paginated, searchable list of Dealerships.
5. As a Staff Member, I want a home-shop dashboard of Appointment status counts and Notification send/fail/bounce/open, for today and year-to-date, plus last 7 days.

## Rules

- Creating a Dealership attaches the creator as Staff Member of that shop (home Dealership).
- Dealership has an IANA **Dealership Timezone**. Staff GET of Appointments uses it. Mail uses **Booking Offset** from `scheduledAt`, not this zone.
- One home Dealership per Staff Member in v1.
- No Dealership volume cap. Abuse control is one Confirmed Appointment per Vehicle (`APP_ONE_CONFIRMED_PER_VEHICLE`, default on).
- Staff invites and extra roles (admin vs advisor) are later.
- Dashboard is light counts, not a BI suite: one `GET /dashboard/stats?from&to&bucket=` (`DAY` \| `WEEK` \| `MONTH`). Totals plus a zero-filled `buckets[]` in Dealership Timezone so the Staff UI does not fan out today / year / last-7-days. Resource `GET /appointments/stats` and `GET /notifications/stats` stay for totals (optional `bucket`). See [notification.md](notification.md) and [appointment.md](appointment.md).
