# Quarkus Idempotency Extension Demo

A small Quarkus checkout API that shows what the [`quarkus-http-idempotency`](https://github.com/quarkiverse/quarkus-http-idempotency) extension does, and what happens to a retried `POST` with and without it.

Companion article: [Simplifying API retries with the Quarkus HTTP Idempotency extension](https://medium.com/@vinodjagwani/simplifying-api-retries-with-the-quarkus-http-idempotency-extension-25d54d4cde5c)

**Stack:** Quarkus 3.40.1.1, extension 0.4.0, Java 21+, Maven, PostgreSQL (started automatically by Quarkus Dev Services).

## What the extension does

A client that times out will retry, and a retried `POST` can run twice (two orders, two charges). The extension implements the `Idempotency-Key` request header: the client sends one unique key per logical operation and reuses it on every retry. The server runs the operation once, stores the response, and replays it for retries.

With the dependency on the classpath, every `POST` and `PATCH` carrying the header is guarded. There is no code in your resource methods.

| Situation | Result |
|---|---|
| New key | Operation runs, response stored |
| Same key, same body, completed | Stored response replayed, with `Idempotent-Replayed: true` |
| Same key, same body, still running | `409 Conflict` |
| Same key, different body | `422 Unprocessable Content` |
| Key missing (when required) | `400 Bad Request` |

Things to know:

- It stores the response in a pluggable store. This demo uses JDBC (PostgreSQL); Redis 7+ and an in-memory store also exist.
- It is an HTTP-edge guard, not exactly-once business execution. Keep database constraints under your business data.
- It is young (0.x, marked experimental). Pin the version.

More detail is in the article and the [official docs](https://docs.quarkiverse.io/quarkus-http-idempotency/dev/index.html).

## How this demo is set up

| What | Where |
|---|---|
| Extension dependency | `pom.xml` (`io.quarkiverse.idempotency:quarkus-http-idempotency`) |
| Extension settings | `src/main/resources/application.properties` |
| Idempotency table (the extension never creates it) | `src/main/resources/db/migration/V1__demo_schema.sql` (Flyway) |
| Demo tables | same migration: `orders` and `charges` |

Key settings: `quarkus.idempotency.store=jdbc` (shared, durable store), `quarkus.idempotency.require-key=true` (a write without a key is rejected), `quarkus.idempotency.response-ttl=24h` and `lock-ttl=60s`.

Each order inserts one row into `charges`. That row stands in for money leaving the account, so counting rows shows how many times the side effect ran.

## Package layout

Dependencies point one way: `api` → `service` → `repository` → `model`.

| Package | Holds |
|---|---|
| `demo.checkout.api` | `OrderResource` (HTTP only), `CreateOrderRequest` |
| `demo.checkout.service` | `CheckoutService` (orchestrates the use case), `PaymentGateway` (simulated slow provider) |
| `demo.checkout.repository` | `OrderRepository`, `ChargeRepository` (JDBC) |
| `demo.checkout.model` | `Order` |

## Prerequisites

- JDK 21 or newer
- Maven 3.9+
- Docker running (Dev Services starts a PostgreSQL container; the first run pulls the `postgres:18` image, about 140 MB)

## Run the tests

```bash
mvn test
```

Six integration tests run against real PostgreSQL: replay on retry, double charge without the guard, `422` on a changed body, `400` without a key, parallel duplicates, and an invalid body.

## Run the app

```bash
DEMO_PAYMENT_DELAY=PT1S mvn quarkus:dev
```

On Windows PowerShell:

```powershell
$env:DEMO_PAYMENT_DELAY = "PT1S"; mvn quarkus:dev
```

`DEMO_PAYMENT_DELAY` makes the simulated payment step take one second, which keeps the first request "in flight" long enough to see the `409` on a parallel duplicate. Without it the delay is zero. The app listens on `http://localhost:8080`.

## Try it

**1. Without the guard, a retry double-charges.** `/orders/unsafe` has the same logic with the guard switched off (`@Idempotent(enabled = false)`).

```bash
curl -X POST -H 'Content-Type: application/json' -d '{"item":"laptop","amount":999.00}' localhost:8080/orders/unsafe
curl -X POST -H 'Content-Type: application/json' -d '{"item":"laptop","amount":999.00}' localhost:8080/orders/unsafe
curl localhost:8080/orders/charges/count
```

Expect `{"charges":2}`.

**2. With the guard, the retry is replayed.** Send the same key twice:

```bash
curl -i -X POST -H 'Content-Type: application/json' -H 'Idempotency-Key: 8e039c52-6b1f-4a1c-9d3e-2f5a7c41b932' -d '{"item":"laptop","amount":999.00}' localhost:8080/orders
curl -i -X POST -H 'Content-Type: application/json' -H 'Idempotency-Key: 8e039c52-6b1f-4a1c-9d3e-2f5a7c41b932' -d '{"item":"laptop","amount":999.00}' localhost:8080/orders
curl localhost:8080/orders/charges/count
```

Both calls return `201` with the same `Location`. The second has `Idempotent-Replayed: true`, and the charge count goes up by only one.

**3. Reuse the key with a different body:**

```bash
curl -i -X POST -H 'Content-Type: application/json' -H 'Idempotency-Key: 8e039c52-6b1f-4a1c-9d3e-2f5a7c41b932' -d '{"item":"laptop","amount":1.00}' localhost:8080/orders
```

Expect `422` with an `application/problem+json` body.

**4. No key:**

```bash
curl -i -X POST -H 'Content-Type: application/json' -d '{"item":"laptop","amount":999.00}' localhost:8080/orders
```

Expect `400`.

**5. Parallel duplicates.** Send several requests at once with one new key while `DEMO_PAYMENT_DELAY=PT1S` is set. One returns `201` and the others return `409`. Only one charge is made, and a later retry with that key is a replay.

## Endpoints

| Method | Path | Guarded | Notes |
|---|---|---|---|
| POST | `/orders` | yes, key required | creates an order and one charge |
| POST | `/orders/unsafe` | no | same logic, for contrast |
| GET | `/orders/{id}` | n/a | `404` for an unknown id |
| GET | `/orders/charges/count` | n/a | number of charge rows |

Request body for both `POST` endpoints: `{"item":"laptop","amount":999.00}`. `item` must be non-blank and `amount` positive, otherwise the response is `400`.
