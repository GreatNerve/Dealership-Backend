# Dealership (PRD)

## Stories

1. As a Staff Member, I want to create a Dealership and become its Staff Member, so that the shop can book.
2. As a Staff Member, I must not book another Dealership, so that another Venue is always the Customer’s own booking.
3. As a Customer, I want to pick a Dealership when I book, so that I choose the Venue.
4. As a caller, I want a paginated, searchable list of Dealerships.
5. As a Staff Member, I want a home-shop dashboard of Appointment status counts and Notification send/fail/bounce/open, for today, the calendar year (1 Jan–31 Dec, Dealership Timezone), plus last 7 days.
6. As a Staff Member, I want weekly opening hours and a default **Slot Capacity**, so Customers only book **Service Slots** we can run.
7. As a Staff Member, I want a **Capacity Override** (date range, optional time, including holidays at capacity 0) so a specific timeline can drop below the default or close.
8. As a Staff Member, I want a month calendar of Appointment counts at my home Dealership, and to expand a day to see those visits.
9. As a Customer, I want to see which **Service Slots** are available at a Dealership (full, closed, and past times crossed out) before I create an Appointment.

## Rules

- Creating a Dealership attaches the creator as Staff Member of that shop (home Dealership).
- Dealership has an IANA **Dealership Timezone**. Staff GET of Appointments uses it. Mail uses **Booking Offset** from `scheduledAt`, not this zone. **Service Slot** grid, weekly hours, and **Capacity Overrides** use this zone.
- One home Dealership per Staff Member in v1.
- Create seeds weekly hours (Mon–Sat 09:00–18:00, Sunday closed) and `defaultCapacity` from `APP_SLOT_DEFAULT_CAPACITY` (default 10). Test profile may seed 24h hours so clock-relative tests stay slot-aligned only.
- One open/close per weekday, or the weekday is closed. No split shifts in v1.
- **Capacity Overrides** do not overlap (date range and, when both have times, time-of-day). Date-only is all day. Holiday = capacity 0. Cannot open a closed weekday.
- Staff cannot save hours or a capacity (default or override) that would leave existing Confirmed Appointments outside the new open window or with `booked > capacity` (`409 SCHEDULE_CONFLICT`). Cancel or reschedule first.
- No shop-wide daily volume cap. Abuse control is one Confirmed Appointment per Vehicle plus **Service Slot** capacity.
- Staff invites and extra roles (admin vs advisor) are later.
- Dashboard is light counts, not a BI suite: two `GET /dashboard/stats` calls. Year cards omit `bucket` (totals only) for Instant `from`/`to` covering **1 Jan 00:00 through 31 Dec** in Dealership Timezone (not year-to-date through today). Last-7-day charts (and today) use Instant `from`/`to` for those 7 Dealership-local days with `bucket=DAY`. Do not request `DAY` buckets for a year range. Resource `GET /appointments/stats` and `GET /notifications/stats` stay for totals (optional `bucket`). See [notification.md](notification.md) and [appointment.md](appointment.md).
