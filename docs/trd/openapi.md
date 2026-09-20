# OpenAPI / Swagger (TRD)

Interactive API docs, same job as FastAPI’s `/docs`. Pin **2.8.6** with Spring Boot **3.5.3**. Production server URL: `https://dealership.greatnerve.com`.

## Library

Spring Boot has no built-in Swagger UI. Use **springdoc-openapi** (not springfox):

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.8.x</version>
</dependency>
```

Pin a 2.8.x line that matches Spring Boot **3.5**. Do **not** use springdoc 3.x — that is Spring Boot 4.

## URLs (when the app exists)

| What | Path |
| --- | --- |
| Swagger UI | `/swagger-ui.html` (UI also at `/swagger-ui/index.html`) |
| OpenAPI JSON | `/v3/api-docs` |

Every `/api/v1` controller is documented from annotations (`@Operation`, `@Tag`, Bean Validation). No hand-written HTML docs.

## Config (later in `application.yml`)

```yaml
springdoc:
  api-docs.path: /v3/api-docs
  swagger-ui.path: /swagger-ui.html
  swagger-ui.persist-authorization: true
```

OpenAPI `Info`: title Dealership Appointment API, version v1. Servers from config: `app.local-host` and `app.public-host`.

## Authorize (like FastAPI)

Swagger **Authorize** is OAuth2 **password** flow (`OAuth2PasswordBearer`), not a paste-only Bearer box.

- **Username** = account email.
- **Password** = account password.
- Leave **client_secret** empty. Swagger UI may require **client_id**; it is pre-filled as `swagger` and the API ignores it.
- Token URL: `POST /api/v1/auth/login` as `application/x-www-form-urlencoded` (`grant_type=password`, `username`, `password`).
- Login and register are documented with no security requirement so Try-it-out works before Authorize.

JSON `POST /api/v1/auth/login` is wrapped in the standard envelope (`data` holds `{ access_token, token_type, expires_in }`). Swagger Authorize form login is **not** wrapped (OAuth2 needs top-level `access_token`). See [conventions.md](conventions.md).

Documented **2xx** JSON in Swagger matches that envelope (`success`, `data`, `error`, `message`, `correlationId`). `OpenApiCustomizer` wraps schemas because `ResponseBodyAdvice` is runtime-only.

`dev` profile: UI open. After Authorize, Try-it-out sends `Authorization: Bearer <jwt>`.

## Demo

The assignment video may use Swagger instead of curl for `POST /appointments`.
