# Dealership (PRD)

## Stories

1. As a Staff Member, I want to create a Dealership and become its Staff Member, so that the shop can book.
2. As a Staff Member, I must not book another Dealership, so that another Venue is always the Customer’s own booking.
3. As a Customer, I want to pick a Dealership when I book, so that I choose the Venue.
4. As a caller, I want a paginated, searchable list of Dealerships.
5. As a Staff Member, I want a home-shop dashboard of Appointment status counts and Notification send/fail/bounce/open, for today, the calendar year (1 Jan–31 Dec, Dealership Timezone), plus last 7 days.

## Rules

- Creating a Dealership attaches the creator as Staff Member of that shop (home Dealership).
- Dealership has an IANA **Dealership Timezone**. Staff GET of Appointments uses it. Mail uses **Booking Offset** from `scheduledAt`, not this zone.
- One home Dealership per Staff Member in v1.
- No Dealership volume cap. Abuse control is one Confirmed Appointment per Vehicle (`APP_ONE_CONFIRMED_PER_VEHICLE`, default on).
- Staff invites and extra roles (admin vs advisor) are later.
- Dashboard is light counts, not a BI suite: two `GET /dashboard/stats` calls. Year cards omit `bucket` (totals only) for Instant `from`/`to` covering **1 Jan 00:00 through 31 Dec** in Dealership Timezone (not year-to-date through today). Last-7-day charts (and today) use Instant `from`/`to` for those 7 Dealership-local days with `bucket=DAY`. Do not request `DAY` buckets for a year range. Resource `GET /appointments/stats` and `GET /notifications/stats` stay for totals (optional `bucket`). See [notification.md](notification.md) and [appointment.md](appointment.md).
