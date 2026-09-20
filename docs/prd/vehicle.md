# Vehicle (PRD)

## Stories

1. As a Customer, I want to add multiple Vehicles (**Vehicle Number**, make, model, year), so that each car can be serviced.
2. As a Customer, I want a paginated, searchable list of my Vehicles.
3. As a Staff Member, I want to list a Customer’s Vehicles after I found that Customer, so that I can take `vehicleId` for an Appointment.
4. As a Staff Member, I want to add a Vehicle for a walk-in Customer, so that I can book that car.
4. As a Customer with two Vehicles, I want two Confirmed Appointments (one each), so that the cap is per Vehicle, not per person.
5. As a Customer, I want a second Confirmed Appointment on the same Vehicle rejected, so that the car cannot be double-booked.

## Rules

- **Vehicle Number** is unique. Stored uppercase without spaces or hyphens (`KA01AB1234`). Never log the full number.
- At most one **Confirmed** Appointment per Vehicle (global, any Dealership).
- No-Show Expired and Cancelled do not block a new booking.
