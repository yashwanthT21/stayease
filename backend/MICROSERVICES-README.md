# StayEase — Microservices (property + notification)

Two modules from the `stayease-backend` monolith — **property** and
**notification** — extracted into standalone Spring Boot microservices, fronted
by a **Spring Cloud Gateway** and discovered through a **Netflix Eureka**
registry.

property and notification have been **fully extracted**: their code and tables no
longer live in the monolith. The remaining monolith modules that referenced a
property (booking, housekeeping, maintenance) now validate a `propertyId` with a
**remote call to property-service via Eureka discovery**, so the monolith is
itself just another client in the mesh. The other modules (finance, stay, IAM)
are unchanged.

```
                         ┌──────────────────────┐
   client ── JWT ───────▶│   api-gateway :8080  │  verifies JWT once, routes by path,
                         │  (Spring Cloud GW)   │  forwards X-User-* headers
                         └───────┬──────┬───────┘
                                 │      │
              lb://property-svc  │      │  lb://notification-service
                                 ▼      ▼
        ┌───────────────────────────┐  ┌──────────────────────────────┐
        │ property-service :8081    │  │ notification-service :8082    │
        │ DB: stayease_property     │  │ DB: stayease_notification     │
        └─────────────┬─────────────┘  └──────────────▲───────────────┘
                      │  @FeignClient("notification-service")
                      └───────────────────────────────┘   "property created",
                                                          "manager assigned"

        all four register with ──▶  eureka-server :8761

   monolith stayease-backend :8085 is now ALSO a eureka client — booking/
   housekeeping/maintenance call property-service to validate a propertyId, and
   property-service calls BACK into the monolith's IAM (@FeignClient("stayease-
   backend")) to name a user in a notification. The gateway also statically
   proxies /api/auth/** to the monolith so you can log in and get a token.
```

## Inter-service calls: OpenFeign

Every outbound call between services is a **declarative `@FeignClient`
interface** — the HTTP verb, path and body live in the method signature, and
Spring Cloud LoadBalancer resolves the `name` from Eureka, so no code names a
host or port.

| Caller | Client interface | Target |
|---|---|---|
| monolith | `PropertyFeignClient` | `property-service` |
| monolith | `NotificationFeignClient` | `notification-service` |
| property-service | `NotificationFeignClient` | `notification-service` |
| property-service | `UserFeignClient` | `stayease-backend` (IAM) |

Each Feign interface is paired with a plain `@Component` wrapper
(`PropertyClient`, `NotificationClient`, `UserClient`) that owns the **policy**:
which failures are fatal, which mean "treat as absent", and how several calls
combine into one business operation. The interface fails loudly; the wrapper
decides what that means. Callers inject the wrapper, never the Feign proxy.

To pin a client to a fixed address (an environment without Eureka), set
`spring.cloud.openfeign.client.config.<service-name>.url`.

**Identity propagation.** IAM is the one target that requires a JWT.
property-service's `FeignClientConfig` registers a `RequestInterceptor` that
copies the incoming request's `Authorization` header onto the outbound call, so
the lookup runs with exactly the permissions the human caller already had — no
service credential to manage. When there is no header to copy (a background
thread, or a call that bypassed the gateway) the lookup simply fails and the
caller degrades gracefully.

## Versions

| Thing              | Version                                   |
|--------------------|-------------------------------------------|
| Java               | 21                                        |
| Spring Boot        | 4.0.6 (same as the monolith)              |
| Spring Cloud       | 2025.1.2 (**Oakwood** — the train for Boot 4.0.x) |
| Gateway starter    | `spring-cloud-starter-gateway-server-webflux` |
| Registry           | `spring-cloud-starter-netflix-eureka-{server,client}` |
| Inter-service HTTP | `spring-cloud-starter-openfeign`           |
| DB                 | MySQL 8                                    |

## Prerequisites

- JDK 21, Maven 3.9+
- MySQL running on `localhost:3306` (user/pass default to `root`/`root`; override
  with `DB_USERNAME` / `DB_PASSWORD`). Each service creates its own database on
  first run (`createDatabaseIfNotExist=true`) and Flyway builds its schema.

## Run order

Start these in separate terminals, in order. Each is a normal Boot app:

```bash
# 1) registry — wait until it's up (http://localhost:8761)
cd eureka-server        && mvn spring-boot:run

# 2) the two services — they register with Eureka
cd notification-service && mvn spring-boot:run
cd property-service     && mvn spring-boot:run

# 3) the edge
cd api-gateway          && mvn spring-boot:run

# the monolith (login/token + the other modules). Now a eureka client too — start
# it after eureka-server; it needs property-service reachable to validate a
# propertyId when creating reservations/turnovers/maintenance issues.
cd stayease-backend     && mvn spring-boot:run
```

Check the Eureka dashboard at <http://localhost:8761> — you should see
`API-GATEWAY`, `PROPERTY-SERVICE`, and `NOTIFICATION-SERVICE` registered.

Per-service Swagger UIs: <http://localhost:8081/swagger-ui.html> (property),
<http://localhost:8082/swagger-ui.html> (notification).

## Trying it end-to-end (through the gateway on :8080)

1. **Get a JWT** (from IAM in the monolith, proxied through the gateway):

   ```bash
   # register once (role e.g. OWNER / PROPERTY_MANAGER / ADMIN)
   curl -X POST http://localhost:8080/api/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"name":"Ada","email":"ada@example.com","phone":"123","role":"OWNER","password":"secret123"}'
   # -> returns { "token": "...", ... }
   ```

2. **Call a service through the gateway** with that token. The gateway rejects any
   request to `/api/**` (except `/api/auth/**`) that has no valid token with 401.

   ```bash
   TOKEN=... # paste the token

   # create a property -> also fires a "property created" notification to the owner
   curl -X POST http://localhost:8080/api/properties \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"ownerId":1,"title":"Sea View Villa","type":"VILLA","city":"Goa",
          "maxGuests":6,"bedrooms":3,"bathrooms":2}'

   # the owner's notifications (created by property-service via the inter-service call)
   curl "http://localhost:8080/api/notifications?userId=1" -H "Authorization: Bearer $TOKEN"

   # mark one read
   curl -X PATCH http://localhost:8080/api/notifications/1/read -H "Authorization: Bearer $TOKEN"
   ```

## Endpoints

**property-service** (routed via `/api/properties`, `/api/availability`)
- `POST/GET/GET{id}/PUT{id}/DELETE{id} /api/properties` (`?ownerId=` filter on list)
- `POST/GET/GET{id}/PUT{id}/DELETE{id} /api/availability` (`?propertyId=` required on list).
  Dates before today are rejected with a 400 — availability is set from today forward.

> `/api/pricing-rules` was removed: no client ever called it. The `pricing_rules`
> table and its entity remain only so deleting a property cleans up its old rows.

**notification-service** (routed via `/api/notifications`)
- `POST/GET/GET{id}/PUT{id}/DELETE{id} /api/notifications` (`?userId=` and/or `?status=` filter on list)
- `PATCH /api/notifications/{id}/read` — mark read (new)
- `PATCH /api/notifications/{id}/dismiss` — dismiss (new)

## Bugs fixed during extraction

1. **`ResourceNotFoundException` printed `System.out.println("user not found!")`** on
   *every* not-found across the whole app (properties, availability, pricing,
   notifications) with a misleading message. Removed.
2. **Notification list ignored a status-only filter.** `GET /api/notifications?status=UNREAD`
   (no `userId`) fell through to `findAll()` and returned everything. Added a
   `findByStatus` branch so the filter is honoured.
3. **Deleting a property with availability/pricing rows returned 500.** The child
   FKs rejected the delete and it surfaced as an opaque 500. `delete()` now
   cascades (removes availability + pricing first), and a
   `DataIntegrityViolationException` handler maps any residual constraint failure
   to a clean **409**.

## Real-life business logic added

- **Notification mark-read / dismiss** — lightweight `PATCH` transitions instead
  of forcing a full-body `PUT` just to flip a status.
- **Property → owner notification on creation** — the realistic cross-service
  side effect, and the demo's inter-service call (Eureka + OpenFeign).
  Best-effort: a notification outage never fails property creation.
- **Owner → manager assignment notification** — assigning a `managerId` to a
  property tells THAT manager the property is now theirs, naming the owner who
  handed it over (`"You have been assigned to \"Sea Breeze Villa\" by Ada
  Owner."`). Fires on create and update, but only when the assignment actually
  changed, so editing an unrelated field doesn't nag the manager. The owner's name
  comes from a second inter-service call into IAM; if it fails the message still
  goes out, worded "by the owner".
- **Manager ⇄ housekeeper turnover handover** (monolith, housekeeping module) —
  the two never talk directly, so the notifications *are* the handover:
  assigning a turnover tells the housekeeper (property, date, deadlines), and the
  housekeeper marking their work Completed tells the property's manager, who is
  the only one who can then verify it and set the overall status. Both are
  edge-triggered — a real change of assignee, and the transition *into* completed
  — so re-saves don't repeat themselves. Checklist items are deliberately silent:
  one notification per ticked task would bury the completion message that
  actually needs acting on.
- **Pricing guard** — a `PERCENT` adjustment below `-100%` is rejected (it would
  make the nightly price negative).

## Microservice design decisions (and trade-offs)

- **Database per service.** Each service owns its schema; cross-service foreign
  keys to `users` are dropped and become soft `Long` references. FKs are kept
  only *inside* a service (property ↔ its availability/pricing).
- **Auth at the edge.** The gateway verifies the IAM-issued JWT once (shared HS256
  secret), then forwards `X-User-Id` / `X-User-Role` / `X-User-Email`. Services
  trust those because they only accept traffic from behind the gateway. In
  production you'd also secure service-to-service traffic (mTLS / propagated
  tokens).
- **Dropped the synchronous IAM existence check** that the monolith's
  `PropertyServiceImpl` / `NotificationServiceImpl` did (`userService.existsById`).
  Re-adding it would couple these services back to the monolith's admin-secured
  endpoint. In a complete migration IAM would itself be a service and this would
  be a discovered call (or, better, validated via an event).
  - property-service *does* now call IAM, but only for a **display name**
    (`GET /api/users/{id}/summary`, a deliberately narrow id+name+role shape open
    to any authenticated caller). It is a cosmetic read, not a validation: the
    write proceeds regardless of the outcome, so property-service still doesn't
    depend on IAM being up.
- **Full extraction (no duplicated code).** property and notification were removed
  from the monolith entirely. The three modules that referenced a property
  (booking, housekeeping, maintenance) now call property-service through a
  `PropertyClient` (wrapping `@FeignClient("property-service")`), and
  Flyway **V3** drops the moved tables plus the cross-service foreign keys
  (`fk_reservation_property`, `fk_turnover_property`, `fk_issue_property`,
  `fk_preventive_property`) — those `property_id` columns are now soft references
  validated over HTTP, not by the database.
  - *Consequence:* the monolith now needs Eureka up (and property-service
    reachable) to create a reservation / turnover / maintenance issue, because the
    propertyId check is a live remote call.
  - *Note:* V3 is destructive — it `DROP`s `properties`, `availability_calendars`,
    `pricing_rules`, and `notifications` from the monolith DB. Their data now lives
    in the services' own databases (data migration itself is out of scope).
```
