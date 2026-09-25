# Test report

Full JUnit suite for the Dealership Appointment API. Strategy plans: [docs/testing/](docs/testing/README.md). Inventory below is **by importance** (assignment proofs first).

## Last run

| | |
| --- | --- |
| Command | `./mvnw test` |
| Date | 2026-09-25 |
| Result | **BUILD SUCCESS** |
| Tests | **117** |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Wall time | ~72 s (Maven) |

Harness: Testcontainers Postgres 16 + RabbitMQ 3.13 + Redis 7.4. Profile `test` (rate limits off, Notification mode stub).

Re-run: `./mvnw test` or `make test`. Refresh this file after any suite that changes totals or fails.

### Proof results (rank 1)

| Test | Class | Result | Time |
| --- | --- | --- | --- |
| `concurrentClaimsSendOnce` | `appointment.AppointmentFlowTest` | **PASS** | 0.493 s |
| `crashAfterProviderAcceptBeforeSentRetriesSameKeyOnly` | `appointment.AppointmentFlowTest` | **PASS** | 0.610 s |

Crash test: after provider accept / before durable `SENT`, reclaim retries the same idempotency key; Notification is stored **once** (`SENT`).

---

## Order of importance

| Rank | Why |
| --- | --- |
| 1 | Assignment proofs — never the same Reminder twice; crash window; DB uniqueness |
| 2 | Core delivery & lifecycle — create → due send → cancel / complete / reschedule / replay |
| 3 | Access & auth — home shop / own Customer; JWT; headers; login |
| 4 | Clock SQL & workers — send-window expire, claim batch, lease, notify-off log |
| 5 | Staff booking & HTTP hygiene — directory, walk-in, idempotency, pagination, nested refs |
| 6 | Unit policy & config — fast guards |

---

## 1 — Assignment proofs

Plan: [docs/testing/uniqueness-and-concurrency.md](docs/testing/uniqueness-and-concurrency.md)

| Test | Class | What it proves |
| --- | --- | --- |
| `concurrentClaimsSendOnce` | `appointment.AppointmentFlowTest` | Two workers claim one due Reminder → one stub send, one `SENT` Notification |
| `crashAfterProviderAcceptBeforeSentRetriesSameKeyOnly` | `appointment.AppointmentFlowTest` | Crash after accept / before `SENT` → same key; Notification stored once |
| `duplicateReminderOffsetSameVersionFailsUniqueConstraint` | `appointment.AppointmentFlowTest` | Unique `(appointment_id, offset_minutes, schedule_version)` |
| `createRemindersIdempotencyAndOneConfirmedPerVehicle` | `appointment.AppointmentFlowTest` | Same Idempotency-Key → one Appointment; second Confirmed on same Vehicle → 409 |
| `replayDeadLetterSendsOnceWithSameKey` | `appointment.AppointmentFlowTest` | Dead-letter replay → one send, same key |
| `replayDeadLetterSendsOnceWithSameKey` | `e2e.AppointmentEndToEndTest` | Same proof over HTTP |
| `dueReminderSendsOnceWithBookingOffsetWallTime` | `e2e.AppointmentEndToEndTest` | Due Reminder → stub once; Booking Offset wall time |
| Cap-on path (create + policy) | `appointment.AppointmentPolicyTest` + flow | One Confirmed per Vehicle when cap is on |
| Cap-off IT | `appointment.OneConfirmedPerVehicleOffIT` | Cap off → two Confirmed allowed |

---

## 2 — Core delivery & lifecycle

| Test | Class | What it proves |
| --- | --- | --- |
| `staffBooksHomeDealershipWithoutSendingDealershipId` | `e2e.AppointmentEndToEndTest` | Staff books home shop only |
| `staffSendingDealershipIdIsRejected` | `e2e.AppointmentEndToEndTest` | Client-supplied Dealership id rejected |
| `cancelBeforeDueDoesNotSend` | `e2e.AppointmentEndToEndTest` | Cancel before due → no Notification |
| `staffCompleteFreesVehicleAndCustomerCannotComplete` | `e2e.AppointmentEndToEndTest` | Staff complete; Customer complete → 403 |
| `rescheduleCancelsOldRemindersAndOpensNewOffsets` | `e2e.AppointmentEndToEndTest` | Reschedule bumps schedule version; new offsets |
| `notifyOffAppendsLogAndStoresNotification` | `e2e.AppointmentEndToEndTest` | `notify: false` → log + Notification row |
| `staffReadsRemindersNotScheduledUntilDue` | `appointment.AppointmentFlowTest` | `NOT_SCHEDULED` until a row exists |
| `replayOutsideHomeShopIsNotFound` | `appointment.AppointmentFlowTest` | Replay not at home Dealership → 404 |
| `replayPendingIsConflict` | `appointment.AppointmentFlowTest` | Replay while pending → 409 |

---

## 3 — Access & auth

| Test | Class | What it proves |
| --- | --- | --- |
| `customerCannotReadAnotherCustomersAppointment` | `e2e.AppointmentEndToEndTest` | Other Customer → 404 |
| `staffCannotReadAnotherDealershipAppointment` | `e2e.AppointmentEndToEndTest` | Other shop → 404 |
| `staffWithoutHomeShopCannotListCustomers` | `appointment.AppointmentFlowTest` | No home membership → 404 |
| JSON login + OAuth2 form token | `identity.AuthFlowTest` | Token paths work |
| Nosniff + frame deny; JWT prometheus | `shared.config.SecurityHeadersAndMetricsTest` | Headers; `/actuator/prometheus` + `dealership_appointments_total` |
| Secret &lt; 32 bytes rejected | `shared.security.JwtServiceTest` | JWT secret floor |
| JWT secret length / default secret | `shared.config.AppPropertiesJwtTest` | Weak / default secret refused outside `dev`/`test` |

---

## 4 — Clock SQL & workers

| Test | Class | What it proves |
| --- | --- | --- |
| `tenHoursOutExpiresTwentyFourHourRowInSql` | `appointment.AppointmentFlowTest` | Send-window expire is set-based SQL |
| `twentyHoursOutKeepsTwentyFourHourPending` | `appointment.AppointmentFlowTest` | Still inside window → stays pending |
| `onePollClaimsABatchOfDueReminders` | `appointment.AppointmentFlowTest` | Claim Batch size from config / CPUs |
| `markSentRequiresLiveProcessingLease` | `appointment.AppointmentFlowTest` | Only live `mail-*` owner can mark SENT |
| `notifyOffLogOnlyWhenInsideMidpointWindow` | `appointment.AppointmentFlowTest` | Midpoint window for notify-off log |
| Transient vs permanent; max → dead-letter | `notification.smtp.RetryPolicyTest` | Retry / dead-letter policy |
| SMTP 4xx vs 5xx | `notification.smtp.SmtpFailuresTest` | `421`/`450`/`451`/`452` retry; 5xx and a bad address stay permanent |
| Claim batch / workers config | `shared.config.AppPropertiesWorkersTest` | Worker config binding |
| Hikari / Tomcat / Claim Batch from CPUs | `shared.config.HardwareSizingTest` | Auto sizing |

---

## 5 — Staff booking & HTTP hygiene

| Test | Class | What it proves |
| --- | --- | --- |
| `staffSearchesCustomerAndVehiclesForBookingIds` | `appointment.AppointmentFlowTest` | Staff `GET /customers` (+ vehicles) for ids |
| `staffCreatesWalkInCustomerVehicleAndBooks` | `appointment.AppointmentFlowTest` | Walk-in then book |
| `idempotencyKeyIsScopedToUser` | `appointment.AppointmentFlowTest` | Idempotency-Key unique per User |
| `expiredIdempotencyKeysArePurged` | `appointment.AppointmentFlowTest` | UTC midnight purge |
| `listEndpointsBatchNestedRefs` | `appointment.AppointmentFlowTest` | Nested refs batched, not per-row |
| `listsArePaginatedAndOversizedPageIsRejected` | `e2e.AppointmentEndToEndTest` | `page`/`size`; max size enforced |
| Burst list + create; no `5xx` | `e2e.RequestCapacityTest` | Capacity smoke |
| Email / password / plate / timezone / `q` / envelope | `shared.api.InputValidationTest` | HTTP validation |
| Optimistic lock → `CONCURRENT_UPDATE` | `shared.api.GlobalExceptionHandlerTest` + `ApiErrorCodeTest` | 409 mapping |
| Per-endpoint Redis key | `shared.ratelimit.RateLimitKeysTest` | UUID collapsed to `{id}` |

---

## 6 — Unit policy & config (fast)

| Class | Tests | What it proves |
| --- | --- | --- |
| `appointment.AppointmentPolicyTest` | 5 | Past `scheduledAt` rejected; second Confirmed conflicts; two Vehicles OK |
| `notification.ReminderMailTest` | 5 | Booking Offset wall time; greeting; HTML escape; vehicle line |
| `shared.time.BookingTimesTest` | 7 | Parse offset; mail local time; no host zone |
| `shared.config.ReminderOffsetsTest` | 3 | Config offset list |
| `shared.api.InputsTest` | 2 | Sanitize / email lowercase |
| `vehicle.VehicleNumbersTest` | 3 | Plate normalize / unique form |

---

## Layers (cross-ref)

| Layer | Plan | Where |
| --- | --- | --- |
| Unit | [docs/testing/unit.md](docs/testing/unit.md) | No Spring / no containers |
| Integration | [docs/testing/integration.md](docs/testing/integration.md) | `*FlowTest`, `*IT` via `AbstractIT` |
| End-to-end | [docs/testing/end-to-end.md](docs/testing/end-to-end.md) | `com.dealership.e2e.*` |
| Uniqueness | [docs/testing/uniqueness-and-concurrency.md](docs/testing/uniqueness-and-concurrency.md) | Rank **1** |
| Harness | [docs/testing/harness.md](docs/testing/harness.md) | `AbstractIT`, stub recorder, Testcontainers |
| Manual | [manual-appointment.md](manual-appointment.md) | Curl / `make appointment` (not JUnit) |

---

## Last run — counts by class (importance order)

| Class | Tests | Fail | Err | Skip | Time | Rank |
| --- | --- | --- | --- | --- | --- | --- |
| `appointment.AppointmentFlowTest` | 24 | 0 | 0 | 0 | 47.100 s | 1–5 |
| `e2e.AppointmentEndToEndTest` | 11 | 0 | 0 | 0 | 6.439 s | 1–5 |
| `appointment.AppointmentPolicyTest` | 5 | 0 | 0 | 0 | 0.005 s | 1, 6 |
| `dealership.DashboardStatsTest` | 1 | 0 | 0 | 0 | 0.500 s | 5 |
| `e2e.RequestCapacityTest` | 1 | 0 | 0 | 0 | 1.397 s | 5 |
| `identity.AuthFlowTest` | 1 | 0 | 0 | 0 | 0.295 s | 3 |
| `identity.CredentialsTest` | 1 | 0 | 0 | 0 | 0.456 s | 3 |
| `shared.config.SecurityHeadersAndMetricsTest` | 2 | 0 | 0 | 0 | 1.459 s | 3 |
| `shared.api.InputValidationTest` | 6 | 0 | 0 | 0 | 1.189 s | 5 |
| `notification.smtp.RetryPolicyTest` | 4 | 0 | 0 | 0 | 0.003 s | 4 |
| `notification.smtp.SmtpFailuresTest` | 2 | 0 | 0 | 0 | 0.004 s | 4 |
| `notification.webhook.DeliveryWebhookAdapterTest` | 6 | 0 | 0 | 0 | 0.007 s | 4 |
| `notification.RecordedTest` | 1 | 0 | 0 | 0 | 0.001 s | 6 |
| `shared.config.AppPropertiesWorkersTest` | 4 | 0 | 0 | 0 | 0.005 s | 4 |
| `shared.config.HardwareSizingTest` | 3 | 0 | 0 | 0 | 0.002 s | 4 |
| `shared.security.JwtServiceTest` | 1 | 0 | 0 | 0 | 0.001 s | 3 |
| `shared.security.SecretsTest` | 2 | 0 | 0 | 0 | 0.001 s | 3 |
| `shared.config.AppPropertiesJwtTest` | 2 | 0 | 0 | 0 | 0.006 s | 3 |
| `shared.api.ApiErrorCodeTest` | 1 | 0 | 0 | 0 | 0.000 s | 5 |
| `shared.api.GlobalExceptionHandlerTest` | 1 | 0 | 0 | 0 | 0.002 s | 5 |
| `shared.api.InstantRangeTest` | 3 | 0 | 0 | 0 | 0.004 s | 5 |
| `shared.api.StatsBucketTest` | 5 | 0 | 0 | 0 | 0.006 s | 5 |
| `shared.ratelimit.RateLimitKeysTest` | 4 | 0 | 0 | 0 | 0.005 s | 5 |
| `shared.ratelimit.ClientIpsTest` | 4 | 0 | 0 | 0 | 2.696 s | 5 |
| `shared.config.JacksonSanitizeTest` | 1 | 0 | 0 | 0 | 0.005 s | 6 |
| `shared.db.SqlValuesTest` | 1 | 0 | 0 | 0 | 0.001 s | 6 |
| `notification.ReminderMailTest` | 5 | 0 | 0 | 0 | 0.007 s | 6 |
| `shared.time.BookingTimesTest` | 7 | 0 | 0 | 0 | 0.007 s | 6 |
| `shared.config.ReminderOffsetsTest` | 3 | 0 | 0 | 0 | 0.003 s | 6 |
| `shared.api.InputsTest` | 2 | 0 | 0 | 0 | 0.003 s | 6 |
| `vehicle.VehicleNumbersTest` | 3 | 0 | 0 | 0 | 0.002 s | 6 |
| **Total** | **117** | **0** | **0** | **0** | | |

Package prefix: `com.dealership.`.

---

## Not in default `./mvnw test`

| Slice | Notes |
| --- | --- |
| Rate-limit HTTP burst | [docs/testing/end-to-end.md](docs/testing/end-to-end.md) (`test-ratelimit`); off by default |
| Manual Appointment | [manual-appointment.md](manual-appointment.md) / `make appointment` |
| `appointment.OneConfirmedPerVehicleOffIT` | Class name ends in `IT`. This `./mvnw test` run did not execute it |
