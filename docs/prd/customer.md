# Customer (PRD)

## Stories

1. As a Customer, I want a profile with contact details, so that Reminders have a destination.
2. As a Staff Member, I want to search Customers and see their Vehicles, so that I can copy `customerId` and `vehicleId` for a walk-in Appointment.
3. As a Staff Member, I want to create a walk-in Customer and add their Vehicle, so that I can book them at my home Dealership without them self-registering first.

## Rules

- A Customer owns one or more Vehicles.
- No Customer timezone on register or profile. Offset travels with `scheduledAt`.
- Staff may search the Customer directory (`q` on contact or Vehicle Number / make / model) and list that Customer’s Vehicles. That is how Staff obtain ids for `POST /appointments`. Logs still mask contact.
- Staff may `POST /customers` (email, optional `name`, password; role always Customer) and `POST /customers/{id}/vehicles`. Then Staff `POST /appointments` with those ids at **home Dealership only**.
