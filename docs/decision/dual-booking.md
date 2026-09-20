# Dual booking, staff pinned to home shop

Two ways in:

- Customer: `{ vehicleId, dealershipId, scheduledAt }` — they pick the Venue.
- Staff: `{ customerId, vehicleId, scheduledAt }` — shop is **always their home Dealership**.

I considered letting Staff book another Venue “because the Customer asked.” That lets any staff token fill any calendar. I reversed it: other Venue means the **Customer** books that shop. JWT roles `CUSTOMER` and `DEALERSHIP_STAFF`; access token **1 day**; `dev` may skip auth for the curl demo. List GETs are paginated and searchable (`q`). Customer reads own; Staff reads home shop only (else 404).
