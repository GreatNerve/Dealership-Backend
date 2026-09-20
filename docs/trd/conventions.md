# HTTP conventions

Base path `/api/v1`. JSON.

Error body:

```json
{
  "error": "VEHICLE_ALREADY_CONFIRMED",
  "message": "This vehicle already has a confirmed appointment",
  "correlationId": "uuid"
}
```

**Loading / dependency failure:** list/get must not hang without a timeout. On DB/broker outage return `503` with `RETRYABLE`. Never return an empty `200` that looks like “no rows” when the read failed.

**Auth:** Bearer JWT unless `dev` profile skips it. Access token **1 day**.

**Idempotency:** required on `POST /appointments`. See [appointment.md](appointment.md).

**Pagination:** all list GETs. See [pagination.md](pagination.md).

**Search:** optional `q` on all list GETs. See [search.md](search.md).

**OpenAPI:** springdoc UI like FastAPI `/docs`. See [openapi.md](openapi.md).

**Time:** UTC Instant stored; **Booking Offset** from `scheduledAt`; Staff GET uses Dealership Timezone. No Customer timezone field. See [time.md](time.md).

**Code:** reuse, no copy-paste. See [code-style.md](code-style.md).
