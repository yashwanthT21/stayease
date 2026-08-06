# How to run StayEase (microservices) + manual test

All commands are **PowerShell** (your default shell).

## 0. Prerequisites

- **JDK 21** — you have it (`java -version` → 21.0.2).
- **MySQL 8** running on `localhost:3306`. Default login used by the apps is
  `root` / `root`. If yours differs, set these in **every** service terminal
  *before* starting it:
  ```powershell
  $env:DB_USERNAME = "root"
  $env:DB_PASSWORD = "your-mysql-password"
  ```
  The apps create their databases automatically (`stayease`, `stayease_property`,
  `stayease_notification`).
- No need to install Maven — each project has its own `mvnw.cmd`.

> ⚠️ The monolith's Flyway **V3** migration DROPs the old `properties`,
> `availability_calendars`, `pricing_rules`, and `notifications` tables from the
> `stayease` database (that data now lives in the services). If you have data in
> `stayease` you care about, back it up first.

## 1. Start everything

### The one-liner

```cmd
backend\run-all.cmd
```

That's it. It opens one window per service (titled `StayEase <name>`), starts the
registry first and **waits for port 8761** before launching the other four, so
they always come up in a working order. It also warns you up front if nothing is
listening on MySQL's port, rather than letting five windows fill with connection
stack traces.

Notes:

- The ports it uses are the ones in each `application.yml`: eureka **8761**,
  api-gateway **7000**, property-service **8001**, notification-service **8082**,
  monolith **8085**. (The tables further down this file still say 8080/8081 — the
  script is the accurate list.)
- Re-running it when eureka is already up is safe: it detects that and leaves it
  alone. It does **not** check the other four, so don't run it twice in a row or
  you'll get port-in-use errors.
- To use non-default MySQL credentials, set them first — every window inherits
  the environment:
  ```cmd
  set DB_PASSWORD=your-mysql-password
  backend\run-all.cmd
  ```
- Stop a service with `Ctrl+C` in its window, or just close the window.

### Or start them by hand (5 terminals, in this order)

Open a **separate** PowerShell window per service. Wait for each to log
`Started ...Application` before moving on.

```powershell
# Terminal 1 — service registry (wait for it, then open http://localhost:8761)
cd C:\Users\2506580\Downloads\stayease-backend\eureka-server
.\mvnw.cmd spring-boot:run

# Terminal 2 — notification service (:8082)
cd C:\Users\2506580\Downloads\stayease-backend\notification-service
.\mvnw.cmd spring-boot:run

# Terminal 3 — property service (:8081)
cd C:\Users\2506580\Downloads\stayease-backend\property-service
.\mvnw.cmd spring-boot:run

# Terminal 4 — API gateway (:8080)
cd C:\Users\2506580\Downloads\stayease-backend\api-gateway
.\mvnw.cmd spring-boot:run

# Terminal 5 — the monolith (:8085) — now also a Eureka client
cd C:\Users\2506580\Downloads\stayease-backend\stayease-backend
.\mvnw.cmd spring-boot:run
```

After all are up, **wait ~30–60 s** for Eureka registration to settle, then open
<http://localhost:8761>. You should see: `EUREKA-SERVER` (self), `API-GATEWAY`,
`PROPERTY-SERVICE`, `NOTIFICATION-SERVICE`, `STAYEASE-BACKEND`.

Stop any service with `Ctrl+C` in its window.

## 2. Manual test — core microservices flow (through the gateway :8080)

Paste these into a **6th** PowerShell window.

```powershell
$gw = "http://localhost:8080"

# --- 2a. Register a user via the gateway (proxied to IAM). Grab the JWT. ---
$body = @{ name="Ada Owner"; email="ada@example.com"; password="secret123";
           phone="555-0100"; role="OWNER" } | ConvertTo-Json
$reg  = Invoke-RestMethod -Method Post -Uri "$gw/api/auth/register" `
          -ContentType 'application/json' -Body $body
$token   = $reg.token
$ownerId = $reg.userId
$h = @{ Authorization = "Bearer $token" }
"token length = $($token.Length); ownerId = $ownerId"
# (Re-running register with the same email returns 409. Then log in instead:
#  Invoke-RestMethod -Method Post -Uri "$gw/api/auth/login" -ContentType 'application/json' `
#    -Body (@{ email="ada@example.com"; password="secret123" } | ConvertTo-Json) )

# --- 2b. NEGATIVE: no token is rejected at the edge (401). ---
try { Invoke-RestMethod -Method Get -Uri "$gw/api/notifications" }
catch { "Blocked as expected -> $($_.Exception.Response.StatusCode)" }

# --- 2c. Create a property (gateway -> property-service). ---
# This ALSO fires an inter-service call to notification-service to notify the owner.
$prop = @{ ownerId=$ownerId; title="Sea View Villa"; type="VILLA"; city="Goa";
           maxGuests=6; bedrooms=3; bathrooms=2 } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$gw/api/properties" `
             -Headers $h -ContentType 'application/json' -Body $prop
$propertyId = $created.id
$created

# --- 2d. See the auto-generated notification (gateway -> notification-service). ---
$notes = Invoke-RestMethod -Method Get -Uri "$gw/api/notifications?userId=$ownerId" -Headers $h
$notes          # expect a PROPERTY notification: "Your property ... has been created."

# --- 2e. Mark it read (new PATCH endpoint). ---
$noteId = $notes[0].id
Invoke-RestMethod -Method Patch -Uri "$gw/api/notifications/$noteId/read" -Headers $h

# --- 2f. Add availability for the property. ---
# calendarDate must be today or later; a past date is rejected with a 400.
$avail = @{ propertyId=$propertyId; calendarDate=(Get-Date).ToString('yyyy-MM-dd');
            basePrice=150.00; minimumNights=2 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$gw/api/availability" -Headers $h `
  -ContentType 'application/json' -Body $avail
```

> `/api/pricing-rules` no longer exists — nothing consumed it, so the endpoint was
> removed. The `pricing_rules` table stays behind only for cascade cleanup when a
> property is deleted.

## 3. Manual test — the monolith now calls property-service

Reservations/guests live in the monolith (:8085) and are **not** routed through
the gateway, but they use the same JWT. Creating a reservation makes the monolith
validate the `propertyId` with a live call to property-service.

```powershell
$mono = "http://localhost:8085"

# a guest profile (userId can be any real user; reuse the one we registered)
$guest = @{ userId=$ownerId; name="Ada Guest"; email="ada.guest@example.com" } | ConvertTo-Json
$gp = Invoke-RestMethod -Method Post -Uri "$mono/api/guests" -Headers $h `
        -ContentType 'application/json' -Body $guest
$guestId = $gp.id

# reservation with a REAL propertyId -> succeeds (property-service confirmed it)
$res = @{ propertyId=$propertyId; guestId=$guestId; checkInDate="2026-08-01";
          checkOutDate="2026-08-05"; guestCount=2; baseAmount=600.00 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$mono/api/reservations" -Headers $h `
  -ContentType 'application/json' -Body $res

# reservation with a BOGUS propertyId -> 404, because property-service says no
$bad = @{ propertyId=999999; guestId=$guestId; checkInDate="2026-08-01";
          checkOutDate="2026-08-05"; guestCount=2; baseAmount=600.00 } | ConvertTo-Json
try { Invoke-RestMethod -Method Post -Uri "$mono/api/reservations" -Headers $h `
        -ContentType 'application/json' -Body $bad }
catch { "Rejected via remote check -> $($_.Exception.Response.StatusCode)" }
```

## 3b. Manual test — the two cross-role notification flows

Both are inter-service calls over OpenFeign. Continue in the same window as §3.

```powershell
# --- Owner assigns a MANAGER to the property (property-service -> IAM for the
#     owner's name, then -> notification-service for the manager). ---
$m = @{ name="Raj Manager"; email="raj@example.com"; password="secret123";
        phone="555-0101"; role="PROPERTY_MANAGER" } | ConvertTo-Json
$mgr = Invoke-RestMethod -Method Post -Uri "$gw/api/auth/register" -ContentType 'application/json' -Body $m
$hm  = @{ Authorization = "Bearer $($mgr.token)" }

$assign = @{ ownerId=$ownerId; managerId=$mgr.userId; title="Sea View Villa"; type="VILLA";
             city="Goa"; maxGuests=6; bedrooms=3; bathrooms=2 } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$gw/api/properties/$propertyId" -Headers $h `
  -ContentType 'application/json' -Body $assign

# expect: 'You have been assigned to "Sea View Villa" by Ada Owner.'
Invoke-RestMethod -Method Get -Uri "$gw/api/notifications?userId=$($mgr.userId)" -Headers $hm |
  ForEach-Object { "[$($_.category)] $($_.message)" }
# Re-running the same PUT sends NOTHING further — only a real change of manager
# notifies, so editing a title doesn't nag them.

# --- Manager assigns a turnover to a HOUSEKEEPER (monolith :8085). ---
$k = @{ name="Meera Clean"; email="meera@example.com"; password="secret123";
        phone="555-0102"; role="HOUSEKEEPING" } | ConvertTo-Json
$hk = Invoke-RestMethod -Method Post -Uri "$gw/api/auth/register" -ContentType 'application/json' -Body $k
$hh = @{ Authorization = "Bearer $($hk.token)" }

$turn = @{ propertyId=$propertyId; assignedToId=$hk.userId; assignedDate="2027-01-10";
           startByTime="2027-01-10T11:00:00"; completeByTime="2027-01-10T14:00:00" } | ConvertTo-Json
$t = Invoke-RestMethod -Method Post -Uri "$mono/api/turnovers" -Headers $hm `
       -ContentType 'application/json' -Body $turn

# expect: 'New turnover assigned to you: Sea View Villa (Goa) on 2027-01-10. ...'
Invoke-RestMethod -Method Get -Uri "$gw/api/notifications?userId=$($hk.userId)" -Headers $hh |
  ForEach-Object { "[$($_.category)] $($_.message)" }

# --- Housekeeper finishes the clean -> the MANAGER is told, because only they
#     can verify it and set the overall status. ---
Invoke-RestMethod -Method Patch `
  -Uri "$mono/api/turnovers/$($t.id)/housekeeper-status?value=COMPLETED" -Headers $hh

# expect: 'Meera Clean has completed the turnover for Sea View Villa (Goa) ...'
Invoke-RestMethod -Method Get -Uri "$gw/api/notifications?userId=$($mgr.userId)" -Headers $hm |
  ForEach-Object { "[$($_.category)] $($_.message)" }
# Re-sending COMPLETED sends nothing more; only the transition INTO completed is news.

# Ticking off checklist items is deliberately silent — one notification per task
# would bury the completion message above.
Invoke-RestMethod -Method Post -Uri "$mono/api/checklists" -Headers $hh -ContentType 'application/json' `
  -Body (@{ turnoverId=$t.id; taskName="Strip and remake all beds"; category="LAUNDRY" } | ConvertTo-Json)
```

## 4. Optional — prove the notification call is best-effort

Stop **notification-service** (`Ctrl+C` in Terminal 2), then create another
property (step 2c). It still returns **201** — the property is saved and the
failed notification is only logged (`Could not send property-created
notification ...`). Restart notification-service afterwards.

## Useful URLs

| What | URL |
|---|---|
| Eureka dashboard | <http://localhost:8761> |
| Gateway | <http://localhost:8080> |
| property-service Swagger | <http://localhost:8081/swagger-ui.html> |
| notification-service Swagger | <http://localhost:8082/swagger-ui.html> |
| monolith Swagger | <http://localhost:8085/swagger-ui.html> |

## Troubleshooting

- **503 / "Load balancer does not contain an instance" from the gateway** — the
  target service isn't registered yet. Wait for it in the Eureka dashboard (~30 s
  after it starts) and retry.
- **Monolith reservation create returns 500 mentioning connection refused** —
  property-service isn't up/registered. Start it first.
- **Flyway checksum/validation error on the monolith** — you changed an already
  applied migration. For a dev reset, drop the `stayease` database and let it
  recreate.
- **`.\mvnw.cmd` "running scripts is disabled"** — allow it for this shell:
  `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`.
