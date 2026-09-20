# HTTP conventions

Base path `/api/v1`. JSON.

The `error` field is `ApiErrorCode` (Java enum). JSON still prints the name (`INVALID_TIMEZONE`, `ALREADY_SENT`). Do not invent a string at the throw site.

Every `/api/v1` JSON body uses one envelope so a frontend can branch on `success`:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "message": null,
  "correlationId": "uuid"
}
```

```json
{
  "success": false,
  "data": null,
  "error": "VEHICLE_ALREADY_CONFIRMED",
  "message": "This vehicle already has a confirmed appointment",
  "correlationId": "uuid"
}
```

HTTP status still matches the error (400/401/403/404/409/429/503). 401 and 403 from the security filter use this same body (never an empty payload). Exception: Swagger Authorize form `POST /auth/login` stays `{ access_token, token_type, expires_in }` so OAuth2 password flow works.

**Loading / dependency failure:** list/get must not hang without a timeout. On DB/broker outage return `503` with `RETRYABLE`. Never return an empty `200` that looks like “no rows” when the read failed.

**Auth:** Bearer JWT unless `dev` profile skips it. Access token **1 day**. Swagger Authorize uses OAuth2 password (username = email) against `POST /auth/login`; see [openapi.md](openapi.md).

**Inputs:** JSON, query, form, and header strings go through `Inputs` (trim, strip ISO control / format / private-use / surrogate). Emails then lowercase. Bean Validation on request records (`@NotBlank`, `@Email`, `@Size`, `@Min`/`@Max`). Failures are `400 VALIDATION_ERROR` (same error JSON). Invalid IANA timezone is `400 INVALID_TIMEZONE`. Oversized `q` is `400 INVALID_Q`. See [code-style.md](code-style.md).

**Idempotency:** required on `POST /appointments`. See [appointment.md](appointment.md).

**Pagination:** all list GETs. Default `size` **100**, max **1000**. See [pagination.md](pagination.md).

**Search:** optional `q` on all list GETs. See [search.md](search.md).

**OpenAPI:** springdoc UI like FastAPI `/docs`. See [openapi.md](openapi.md).

**Time:** UTC Instant stored; **Booking Offset** from `scheduledAt`; Staff GET uses Dealership Timezone. No Customer timezone field. See [time.md](time.md).

**Code:** reuse, no copy-paste. See [code-style.md](code-style.md).
