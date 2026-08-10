# Rate Limiter Service

A standalone Spring Boot service that rate-limits other services — not a
library embedded inside one app, but its own deployable service other APIs
call into before processing a request.

## Architecture (layered MVC)

```
controller/   -> HTTP layer only (request/response, status codes, headers)
service/      -> business logic + transaction boundaries
service/algorithm/ -> the two rate-limit algorithms (Strategy pattern)
repository/   -> Spring Data JPA, including the locking queries
entity/       -> JPA-mapped tables
dto/          -> request/response shapes, decoupled from entities
exception/    -> centralized error handling (@RestControllerAdvice)
```

Why split `ClientConfig` (admin settings) from `TokenBucketState` /
`SlidingWindowState` (live counters)? So that an admin updating a client's
limit never accidentally wipes their current usage — config and live state
are different tables, updated independently.

## How each core requirement is met

| # | Requirement | Where |
|---|---|---|
| 1 | ALLOW/DENY endpoint | `RateLimitController.check()` |
| 2 | Per-client configurable limits via admin endpoint | `AdminController.setLimits()` |
| 3 | State survives restart | `TokenBucketState`/`SlidingWindowState` are MySQL tables, not in-memory maps |
| 4 | Concurrency-safe (no double-spend) | `@Lock(PESSIMISTIC_WRITE)` in the repositories + `@Transactional` in `RateLimitService` — see comments in `TokenBucketStateRepository` |
| 5 | Sliding window mode, selectable per client | `ClientConfig.mode` + `SlidingWindowAlgorithm`, picked via the `Map<RateLimitMode, RateLimitAlgorithm>` strategy lookup in `RateLimitService` |
| 6 | Standard rate-limit headers on every response | `RateLimitController.check()` sets `X-RateLimit-Limit/Remaining/Reset` on both 200 and 429 |
| 7 | Load test proving correctness at 500+ concurrent req/s | `src/test/java/com/ratelimiter/loadtest/ConcurrentLoadTest.java` |

## Why pessimistic locking (not optimistic / not just synchronized)

- `synchronized` in Java only protects against races *within one JVM
  instance* — if you ever run 2+ instances of this service behind a load
  balancer, it stops working. A DB-level lock is correct even then.
- Optimistic locking (`@Version`) works too, but under a real burst you'd
  see a storm of retries as every thread's initial write attempt collides.
  Pessimistic locking just makes the second request wait — simpler to
  reason about, and fine for a rate limiter's key lookup pattern
  (single row, quick transaction).
- Trade-off: this serializes all requests for the *same* clientKey through
  one row lock. Fine for typical per-client load. If you need to push far
  past that, that's exactly the stretch goal — move hot state to Redis and
  use `INCR`/Lua for atomicity instead of a DB row lock.

## Running it locally

Prerequisites: JDK 17, Maven, a local MySQL instance.

1. Create the database (or let `createDatabaseIfNotExist=true` in
   `application.yml` do it), and update the username/password there to
   match your local MySQL.
2. `mvn spring-boot:run`
3. Register a client:
   ```bash
   curl -X POST http://localhost:8080/api/v1/admin/limits \
     -H "Content-Type: application/json" \
     -d '{"clientKey":"user-123","capacity":10,"refillRate":2,"mode":"TOKEN_BUCKET"}'
   ```
4. Hit the check endpoint:
   ```bash
   curl -i -X POST "http://localhost:8080/api/v1/rate-limit/check?clientKey=user-123"
   ```
   Run it 11 times fast — the 11th should come back `429` with
   `X-RateLimit-Remaining: 0`.
5. Try sliding window mode:
   ```bash
   curl -X POST http://localhost:8080/api/v1/admin/limits \
     -H "Content-Type: application/json" \
     -d '{"clientKey":"user-456","capacity":5,"refillRate":0,"mode":"SLIDING_WINDOW","windowSizeMs":1000}'
   ```
6. Run the load test (see instructions in `ConcurrentLoadTest.java`).

## What's intentionally NOT here (stretch goals, not core requirements)

- Distributed mode across multiple limiter instances sharing state (would
  mean moving from a DB row lock to Redis + Lua for atomic ops).
- A live dashboard of allow/deny rates per client.

Both are natural next steps once the core service above is solid.
