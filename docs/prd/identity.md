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
3. As a caller, I want login and register rate-limited, so that brute force fails.

## Rules

- JWT in v1. Roles: `CUSTOMER`, `DEALERSHIP_STAFF`. Access token **1 day**. No refresh token in v1.
- Passwords hashed (BCrypt). Email unique after trim + lowercase.
- `dev` profile may skip auth for the curl demo.
- Contact for Reminders comes from the Customer profile email (register email). No phone in v1. Staff create does not send contact.
