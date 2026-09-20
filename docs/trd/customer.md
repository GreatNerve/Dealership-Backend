# Customer (TRD)

Customer profile is created with Customer register (role `CUSTOMER`). Contact is the register **email** (no phone in v1). Do not log full email; mask in logs (`j***@x.com`). Stub/Brevo payload uses that email. No timezone column on `customers`.

v1: no Staff “create Customer” endpoint. Demo seed supplies a Customer for Staff booking.

Staff Appointment create must pass a `customerId` that exists; Vehicle must belong to that Customer or `409`.
