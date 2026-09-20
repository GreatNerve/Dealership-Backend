# OpenAPI / Swagger (TRD)

Interactive API docs, same job as FastAPI’s `/docs`. **No code until asked.** This file is what we add when implementing.

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
```

OpenAPI `Info`: title Dealership Appointment API, version v1.

`dev` profile: UI open. When JWT is on, springdoc still lists Bearer; Try-it-out sends the token.

## Demo

The assignment video may use Swagger instead of curl for `POST /appointments`.
