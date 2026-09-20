# Vehicle (PRD)

## Stories

1. As a Customer, I want to add multiple Vehicles (VIN, make, model, year), so that each car can be serviced.
2. As a Customer, I want a paginated, searchable list of my Vehicles.
3. As a Customer with two Vehicles, I want two Confirmed Appointments (one each), so that the cap is per Vehicle, not per person.
4. As a Customer, I want a second Confirmed Appointment on the same Vehicle rejected, so that the car cannot be double-booked.

## Rules

- VIN is unique (stored uppercase). Never log the full VIN.
- At most one **Confirmed** Appointment per Vehicle (global, any Dealership).
- No-Show Expired and Cancelled do not block a new booking.
