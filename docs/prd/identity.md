# Identity (PRD)

## Actors

| Actor | Meaning |
| --- | --- |
| User | Login identity. Either a Customer or a Staff Member in v1, not both. |
| Customer | Owns Vehicles; self-books any Dealership. |
| Staff Member | Books on behalf of a Customer at home Dealership only. |
| System | Expires no-shows; claims due work; retries Notifications. |

## Stories

1. As a Customer, I want to register and log in, so that my bookings are mine.
2. As a Staff Member, I want to register and log in, so that I can book for walk-in Customers.
3. As a Staff Member, I want to create a walk-in Customer User, so that they exist before I allot an Appointment.
4. As a caller, I want login and register each rate-limited on their own endpoint (15 / 60s), so that brute force fails without locking a reviewer out for minutes.

## Rules

- JWT in v1. Roles: `CUSTOMER`, `DEALERSHIP_STAFF`. Access token **1 day**. No refresh token in v1.
- Passwords hashed (BCrypt). Email unique after sanitize (trim, strip control/format characters) + lowercase.
- `dev` profile may skip auth for the curl demo.
- Contact for Reminders comes from the Customer profile email (register email or Staff-created Customer). No phone in v1.
