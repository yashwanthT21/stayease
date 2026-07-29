# =====================================================================
# StayEase backend — black-box QA test harness
# External-client only. Does NOT modify app code, config, or data model.
# Creates its own unique test data and cleans it up at the end.
# Requires the app running on http://localhost:8085 + MySQL.
# =====================================================================
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8085'
$script:results = New-Object System.Collections.ArrayList
$script:seq = 0

# Unique suffix so re-runs never collide on unique columns (e.g. email).
$sfx = "$(Get-Random -Maximum 99999)$(Get-Date -Format 'HHmmss')"

function Add-Result($category, $name, $method, $path, $expect, $actual, $ms, $pass, $note) {
    $script:seq++
    [void]$script:results.Add([PSCustomObject]@{
        seq      = $script:seq
        category = $category
        name     = $name
        method   = $method
        path     = $path
        expect   = ($expect -join '/')
        actual   = $actual
        ms       = $ms
        result   = if ($pass) { 'PASS' } else { 'FAIL' }
        note     = $note
    })
    $color = if ($pass) { 'Green' } else { 'Red' }
    Write-Host ("[{0,3}] {1,-5} {2,-14} {3,-6} {4,-34} exp={5,-7} got={6,-5} {7,5}ms  {8}" -f `
        $script:seq, $(if($pass){'PASS'}else{'FAIL'}), $category, $method, $path, ($expect -join '/'), $actual, $ms, $name) -ForegroundColor $color
}

# Core HTTP call. $Body may be a hashtable (-> JSON) or a raw string (sent as-is).
function Api {
    param(
        [string]$Method, [string]$Path, $Body, [string]$Token,
        [int[]]$Expect, [string]$Category, [string]$Name
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $p = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($null -ne $Body) {
        if ($Body -is [string]) { $p['Body'] = $Body } else { $p['Body'] = ($Body | ConvertTo-Json -Depth 6) }
        $p['ContentType'] = 'application/json'
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $actual = 0; $content = ''
    try {
        $resp = Invoke-WebRequest @p
        $actual = [int]$resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $actual = [int]$_.Exception.Response.StatusCode
            try {
                $rd = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $content = $rd.ReadToEnd(); $rd.Close()
            } catch {}
        } else { $content = "CONNERR: $($_.Exception.Message)" }
    }
    $sw.Stop()
    $ms = [int][math]::Round($sw.Elapsed.TotalMilliseconds)
    $pass = $Expect -contains $actual
    $snip = if ($content.Length -gt 120) { $content.Substring(0,120) } else { $content }
    Add-Result $Category $Name $Method $Path $Expect $actual $ms $pass $snip
    $json = $null; try { $json = $content | ConvertFrom-Json } catch {}
    return @{ status = $actual; content = $content; json = $json }
}

function Assert($category, $name, $condition, $note) {
    Add-Result $category $name 'CHECK' '-' @('true') $(if($condition){'true'}else{'false'}) 0 $condition $note
}

$today = Get-Date
function D([int]$days) { $today.AddDays($days).ToString('yyyy-MM-dd') }
function DT([int]$days) { $today.AddDays($days).ToString('yyyy-MM-ddTHH:mm:ss') }

Write-Host "`n========== PHASE A: AUTH & REGISTRATION ==========" -ForegroundColor Cyan

function Register($name, $email, $pwd, $role) {
    return Api POST '/api/auth/register' ([ordered]@{ name=$name; email=$email; password=$pwd; phone='+10000000000'; role=$role }) $null @(201) 'Auth' "register $role"
}

$pwd = 'Passw0rd!'
$admin   = (Register 'QA Admin'   "admin_$sfx@stayease.test"   $pwd 'ADMIN').json
$finance = (Register 'QA Finance' "finance_$sfx@stayease.test" $pwd 'FINANCE').json
$owner   = (Register 'QA Owner'   "owner_$sfx@stayease.test"   $pwd 'OWNER').json
$guestU  = (Register 'QA GuestU'  "guest_$sfx@stayease.test"   $pwd 'GUEST').json
$worker  = (Register 'QA Cleaner' "clean_$sfx@stayease.test"   $pwd 'HOUSEKEEPING').json

$adminTok = $admin.token; $finTok = $finance.token; $ownerTok = $owner.token; $guestTok = $guestU.token
$ownerId = $owner.userId; $guestUserId = $guestU.userId; $workerId = $worker.userId

Assert 'Auth' 'register returns token' ([bool]$adminTok) "token len=$($adminTok.Length)"
Assert 'Auth' 'register returns role ADMIN' ($admin.role -eq 'ADMIN') "role=$($admin.role)"

# login round-trip
$login = Api POST '/api/auth/login' ([ordered]@{ email="admin_$sfx@stayease.test"; password=$pwd }) $null @(200) 'Auth' 'login valid'
Assert 'Auth' 'login returns usable token' ([bool]$login.json.token) ''

# negative auth
Api POST '/api/auth/register' ([ordered]@{ name='Dup'; email="admin_$sfx@stayease.test"; password=$pwd; role='GUEST' }) $null @(409) 'Auth-Neg' 'register duplicate email' | Out-Null
Api POST '/api/auth/register' ([ordered]@{ name='NoEmail'; password=$pwd; role='GUEST' }) $null @(400) 'Auth-Neg' 'register missing email' | Out-Null
Api POST '/api/auth/register' ([ordered]@{ name='BadEmail'; email='not-an-email'; password=$pwd; role='GUEST' }) $null @(400) 'Auth-Neg' 'register invalid email' | Out-Null
Api POST '/api/auth/register' ([ordered]@{ name='Short'; email="short_$sfx@x.test"; password='123'; role='GUEST' }) $null @(400) 'Auth-Neg' 'register password<6' | Out-Null
Api POST '/api/auth/register' "{`"name`":`"R`",`"email`":`"role_$sfx@x.test`",`"password`":`"$pwd`",`"role`":`"SUPERADMIN`"}" $null @(400) 'Auth-Neg' 'register invalid role enum' | Out-Null
Api POST '/api/auth/login' ([ordered]@{ email="admin_$sfx@stayease.test"; password='wrong' }) $null @(401) 'Auth-Neg' 'login wrong password' | Out-Null
Api POST '/api/auth/login' ([ordered]@{ email="ghost_$sfx@x.test"; password=$pwd }) $null @(401) 'Auth-Neg' 'login unknown user' | Out-Null

Write-Host "`n========== PHASE B: AUTHENTICATION GUARDS ==========" -ForegroundColor Cyan
Api GET '/api/properties' $null '' @(401,403) 'AuthN' 'no token -> reject' | Out-Null
Api GET '/api/properties' $null 'this.is.not.a.jwt' @(401,403) 'AuthN' 'malformed token -> reject' | Out-Null

Write-Host "`n========== PHASE C: AUTHORIZATION (RBAC) ==========" -ForegroundColor Cyan
Api GET '/api/users'            $null $guestTok @(403) 'AuthZ' 'GUEST -> /users (deny)'            | Out-Null
Api GET '/api/owner-statements' $null $guestTok @(403) 'AuthZ' 'GUEST -> /owner-statements (deny)' | Out-Null
Api GET '/api/users'            $null $finTok   @(403) 'AuthZ' 'FINANCE -> /users (deny)'          | Out-Null
Api GET '/api/owner-statements' $null $finTok   @(200) 'AuthZ' 'FINANCE -> /owner-statements (allow)' | Out-Null
Api GET '/api/audit-logs'       $null $finTok   @(200) 'AuthZ' 'FINANCE -> /audit-logs (allow)'    | Out-Null
Api GET '/api/users'            $null $adminTok @(200) 'AuthZ' 'ADMIN -> /users (allow)'           | Out-Null
Api GET '/api/audit-logs'       $null $adminTok @(200) 'AuthZ' 'ADMIN -> /audit-logs (allow)'      | Out-Null
Api GET '/api/properties'       $null $guestTok @(200) 'AuthZ' 'GUEST -> /properties (allow)'      | Out-Null

Write-Host "`n========== PHASE D: FUNCTIONAL CRUD + E2E CREATE ==========" -ForegroundColor Cyan
$T = $adminTok   # admin passes every rule; used for data setup

$prop = (Api POST '/api/properties' ([ordered]@{ ownerId=$ownerId; title="Seaside Villa $sfx"; type='VILLA'; city='Lisbon'; maxGuests=6; bedrooms=3; bathrooms=2; amenitiesList='WiFi,Pool'; houseRules='No smoking'; status='LISTED' }) $T @(201) 'CRUD' 'create property').json
$propertyId = $prop.id
Assert 'CRUD' 'property has id' ([bool]$propertyId) "id=$propertyId"

$avail = (Api POST '/api/availability' ([ordered]@{ propertyId=$propertyId; calendarDate=(D 10); availabilityStatus='AVAILABLE'; basePrice=180.00; minimumNights=2 }) $T @(201) 'CRUD' 'create availability').json
$availId = $avail.id
$pricing = (Api POST '/api/pricing-rules' ([ordered]@{ propertyId=$propertyId; ruleType='WEEKEND_SURCHARGE'; startDate=(D 1); endDate=(D 60); adjustment='PERCENT'; adjustmentValue=15; status='ACTIVE' }) $T @(201) 'CRUD' 'create pricing rule').json
$pricingId = $pricing.id
$guest = (Api POST '/api/guests' ([ordered]@{ userId=$guestUserId; name='John Traveller'; email="jt_$sfx@stayease.test"; phone='+1222333444'; nationality='US'; verificationStatus='ID_VERIFIED'; status='ACTIVE' }) $T @(201) 'CRUD' 'create guest profile').json
$guestId = $guest.id
$resv = (Api POST '/api/reservations' ([ordered]@{ propertyId=$propertyId; guestId=$guestId; checkInDate=(D 10); checkOutDate=(D 13); guestCount=2; baseAmount=540.00; cleaningFee=60.00; serviceFee=40.00; bookingSource='DIRECT'; status='PENDING' }) $T @(201) 'CRUD' 'create reservation').json
$reservationId = $resv.id
Assert 'Integrity' 'reservation nights computed=3' ($resv.nights -eq 3) "nights=$($resv.nights)"
Assert 'Integrity' 'reservation total=640 (540+60+40)' ([decimal]$resv.totalAmount -eq 640) "total=$($resv.totalAmount)"

$ci = (Api POST '/api/check-ins' ([ordered]@{ reservationId=$reservationId; guestId=$guestId; actualCheckIn=(DT 10); accessMethod='SMART_LOCK'; welcomePackSent=$true; status='CHECKED_IN' }) $T @(201) 'CRUD' 'create check-in').json
$checkInId = $ci.id
$co = (Api POST '/api/check-outs' ([ordered]@{ reservationId=$reservationId; actualCheckOut=(DT 13); damageNoted=$false; depositReleased=$true; status='CHECKED_OUT' }) $T @(201) 'CRUD' 'create check-out').json
$checkOutId = $co.id
$rev = (Api POST '/api/reviews' ([ordered]@{ reservationId=$reservationId; guestId=$guestId; cleanlinessScore=5; accuracyScore=4; locationScore=5; valueScore=4; comments='Great stay'; status='PUBLISHED' }) $T @(201) 'CRUD' 'create review').json
$reviewId = $rev.id
$turn = (Api POST '/api/turnovers' ([ordered]@{ propertyId=$propertyId; checkOutReservationId=$reservationId; assignedToId=$workerId; assignedDate=(D 13); startByTime=(DT 13); completeByTime=(DT 13); status='PENDING' }) $T @(201) 'CRUD' 'create turnover').json
$turnoverId = $turn.id
$chk = (Api POST '/api/checklists' ([ordered]@{ turnoverId=$turnoverId; taskName='Change linens'; category='LAUNDRY'; completed=$false; status='PENDING' }) $T @(201) 'CRUD' 'create checklist').json
$checklistId = $chk.id
$issue = (Api POST '/api/maintenance-issues' ([ordered]@{ propertyId=$propertyId; reportedById=$ownerId; reportedByType='OWNER'; category='PLUMBING'; description='Leaky tap'; priority='HIGH'; status='OPEN' }) $T @(201) 'CRUD' 'create maintenance issue').json
$issueId = $issue.id
$pm = (Api POST '/api/preventive-maintenance' ([ordered]@{ propertyId=$propertyId; taskName='HVAC service'; frequency='QUARTERLY'; nextScheduledDate=(D 90); status='SCHEDULED' }) $T @(201) 'CRUD' 'create preventive maintenance').json
$pmId = $pm.id
$stmt = (Api POST '/api/owner-statements' ([ordered]@{ ownerId=$ownerId; period='2026-06'; grossRevenue=5400.00; platformFee=540.00; managementFee=270.00; cleaningRevenue=180.00; maintenanceCost=90.00; status='DRAFT' }) $T @(201) 'CRUD' 'create owner statement').json
$statementId = $stmt.id
$payout = (Api POST '/api/owner-payouts' ([ordered]@{ statementId=$statementId; ownerId=$ownerId; amount=4680.00; paymentDate=(D 5); bankAccountRef='IBAN-TEST-001'; status='PENDING' }) $T @(201) 'CRUD' 'create owner payout').json
$payoutId = $payout.id
$notif = (Api POST '/api/notifications' ([ordered]@{ userId=$ownerId; message='Your statement is ready'; category='PAYOUT'; status='UNREAD' }) $T @(201) 'CRUD' 'create notification').json
$notifId = $notif.id
$usr = (Api POST '/api/users' ([ordered]@{ name='Created By Admin'; email="cba_$sfx@stayease.test"; phone='+199'; role='PROPERTY_MANAGER'; status='ACTIVE' }) $T @(201) 'CRUD' 'create user (admin)').json
$createdUserId = $usr.id

Write-Host "`n========== PHASE E: READ-BACK + LIST + FILTER (consistency) ==========" -ForegroundColor Cyan
$g = Api GET "/api/properties/$propertyId" $null $T @(200) 'Read' 'get property by id'
Assert 'Integrity' 'property title round-trips' ($g.json.title -eq "Seaside Villa $sfx") "title=$($g.json.title)"
Api GET "/api/availability/$availId"            $null $T @(200) 'Read' 'get availability by id' | Out-Null
Api GET "/api/pricing-rules/$pricingId"         $null $T @(200) 'Read' 'get pricing by id'       | Out-Null
Api GET "/api/guests/$guestId"                  $null $T @(200) 'Read' 'get guest by id'         | Out-Null
Api GET "/api/reservations/$reservationId"      $null $T @(200) 'Read' 'get reservation by id'   | Out-Null
Api GET "/api/check-ins/$checkInId"             $null $T @(200) 'Read' 'get check-in by id'      | Out-Null
Api GET "/api/check-outs/$checkOutId"           $null $T @(200) 'Read' 'get check-out by id'     | Out-Null
Api GET "/api/reviews/$reviewId"                $null $T @(200) 'Read' 'get review by id'        | Out-Null
Api GET "/api/turnovers/$turnoverId"            $null $T @(200) 'Read' 'get turnover by id'      | Out-Null
Api GET "/api/checklists/$checklistId"          $null $T @(200) 'Read' 'get checklist by id'     | Out-Null
Api GET "/api/maintenance-issues/$issueId"      $null $T @(200) 'Read' 'get issue by id'         | Out-Null
Api GET "/api/preventive-maintenance/$pmId"     $null $T @(200) 'Read' 'get preventive by id'    | Out-Null
Api GET "/api/owner-statements/$statementId"    $null $T @(200) 'Read' 'get statement by id'     | Out-Null
Api GET "/api/owner-payouts/$payoutId"          $null $T @(200) 'Read' 'get payout by id'        | Out-Null
Api GET "/api/notifications/$notifId"           $null $T @(200) 'Read' 'get notification by id'  | Out-Null
Api GET "/api/users/$createdUserId"             $null $T @(200) 'Read' 'get user by id'          | Out-Null

# filtered lists + presence checks (cross-endpoint consistency)
$lp = Api GET "/api/properties?ownerId=$ownerId" $null $T @(200) 'Filter' 'list properties by ownerId'
Assert 'Consistency' 'created property in owner filter' (@($lp.json | Where-Object { $_.id -eq $propertyId }).Count -eq 1) ''
$lr = Api GET "/api/reservations?propertyId=$propertyId" $null $T @(200) 'Filter' 'list reservations by propertyId'
Assert 'Consistency' 'created reservation in property filter' (@($lr.json | Where-Object { $_.id -eq $reservationId }).Count -eq 1) ''
Api GET "/api/availability?propertyId=$propertyId"  $null $T @(200) 'Filter' 'list availability by propertyId' | Out-Null
Api GET "/api/pricing-rules?propertyId=$propertyId" $null $T @(200) 'Filter' 'list pricing by propertyId'      | Out-Null
Api GET "/api/checklists?turnoverId=$turnoverId"    $null $T @(200) 'Filter' 'list checklist by turnoverId'    | Out-Null
$lpay = Api GET "/api/owner-payouts?ownerId=$ownerId" $null $T @(200) 'Filter' 'list payouts by ownerId'
Assert 'Consistency' 'created payout in owner filter' (@($lpay.json | Where-Object { $_.id -eq $payoutId }).Count -eq 1) ''
Api GET "/api/notifications?userId=$ownerId&status=UNREAD" $null $T @(200) 'Filter' 'list notifications by user+status' | Out-Null
Api GET "/api/check-ins?guestId=$guestId"           $null $T @(200) 'Filter' 'list check-ins by guestId'       | Out-Null
Api GET "/api/reviews?guestId=$guestId"             $null $T @(200) 'Filter' 'list reviews by guestId'         | Out-Null

Write-Host "`n========== PHASE F: UPDATE (PUT) + reflect ==========" -ForegroundColor Cyan
Api PUT "/api/properties/$propertyId" ([ordered]@{ ownerId=$ownerId; title="Updated Villa $sfx"; type='VILLA'; city='Porto'; maxGuests=8; bedrooms=4; bathrooms=3; status='LISTED' }) $T @(200) 'Update' 'update property' | Out-Null
$gp = Api GET "/api/properties/$propertyId" $null $T @(200) 'Update' 'get property after update'
Assert 'Consistency' 'property update reflected (city=Porto)' ($gp.json.city -eq 'Porto') "city=$($gp.json.city)"
Api PUT "/api/notifications/$notifId" ([ordered]@{ userId=$ownerId; message='Your statement is ready'; category='PAYOUT'; status='READ' }) $T @(200) 'Update' 'mark notification READ' | Out-Null
$gn = Api GET "/api/notifications/$notifId" $null $T @(200) 'Update' 'get notification after update'
Assert 'Consistency' 'notification status reflected (READ)' ($gn.json.status -eq 'READ') "status=$($gn.json.status)"

Write-Host "`n========== PHASE G: NEGATIVE / EDGE CASES ==========" -ForegroundColor Cyan
Api POST '/api/properties' ([ordered]@{ ownerId=$ownerId; type='VILLA'; city='X'; maxGuests=2; bedrooms=1; bathrooms=1 }) $T @(400) 'Neg' 'property missing title' | Out-Null
Api POST '/api/properties' ([ordered]@{ ownerId=$ownerId; title='Zero'; type='VILLA'; city='X'; maxGuests=0; bedrooms=1; bathrooms=1 }) $T @(400) 'Neg' 'property maxGuests<1' | Out-Null
Api POST '/api/properties' "{`"ownerId`":$ownerId,`"title`":`"BadType`",`"type`":`"VILLA`",`"city`":`"X`",`"maxGuests`":`"NaN`",`"bedrooms`":1,`"bathrooms`":1}" $T @(400) 'Neg' 'property maxGuests wrong type' | Out-Null
Api POST '/api/properties' "{`"ownerId`":$ownerId,`"title`":`"BadEnum`",`"type`":`"CASTLE`",`"city`":`"X`",`"maxGuests`":2,`"bedrooms`":1,`"bathrooms`":1}" $T @(400) 'Neg' 'property invalid enum type' | Out-Null
Api POST '/api/guests' ([ordered]@{ userId=$guestUserId; name='BadEmail'; email='nope' }) $T @(400) 'Neg' 'guest invalid email' | Out-Null
Api POST '/api/reviews' ([ordered]@{ reservationId=$reservationId; guestId=$guestId; cleanlinessScore=0 }) $T @(400) 'Neg' 'review score<1 (minimum)' | Out-Null
Api POST '/api/reservations' ([ordered]@{ propertyId=$propertyId; guestId=$guestId; checkInDate=(D 20); checkOutDate=(D 15); guestCount=2; baseAmount=100.00 }) $T @(400,500) 'Neg' 'reservation checkout<checkin (business rule)' | Out-Null
Api POST '/api/properties' ([ordered]@{ ownerId=999999999; title='GhostOwner'; type='VILLA'; city='X'; maxGuests=2; bedrooms=1; bathrooms=1 }) $T @(400,404,409,500) 'Neg' 'property non-existent ownerId (FK)' | Out-Null
Api POST '/api/properties' '{ this is not json }' $T @(400) 'Neg' 'malformed json body' | Out-Null
Api GET  '/api/properties/999999999' $null $T @(404) 'Neg' 'get property not-found' | Out-Null
Api PUT  '/api/properties/999999999' ([ordered]@{ ownerId=$ownerId; title='x'; type='VILLA'; city='X'; maxGuests=2; bedrooms=1; bathrooms=1 }) $T @(404) 'Neg' 'update property not-found' | Out-Null
Api DELETE '/api/properties/999999999' $null $T @(404,204,500) 'Neg' 'delete property not-found' | Out-Null
Api GET  '/api/properties/abc' $null $T @(400) 'Neg' 'get property non-numeric id (type mismatch)' | Out-Null
Api GET  '/api/availability' $null $T @(400,500) 'Neg' 'list availability missing required propertyId' | Out-Null
Api GET  '/api/pricing-rules' $null $T @(400,500) 'Neg' 'list pricing missing required propertyId' | Out-Null
Api PUT  '/api/auth/login' ([ordered]@{ email='x@x.test'; password='x' }) $null @(405) 'Neg' 'wrong method on /auth/login (405)' | Out-Null

Write-Host "`n========== PHASE H: CLEANUP (reverse FK order) ==========" -ForegroundColor Cyan
$del = @(
    @('/api/notifications', $notifId), @('/api/owner-payouts', $payoutId), @('/api/owner-statements', $statementId),
    @('/api/checklists', $checklistId), @('/api/turnovers', $turnoverId), @('/api/reviews', $reviewId),
    @('/api/check-outs', $checkOutId), @('/api/check-ins', $checkInId), @('/api/reservations', $reservationId),
    @('/api/pricing-rules', $pricingId), @('/api/availability', $availId),
    @('/api/maintenance-issues', $issueId), @('/api/preventive-maintenance', $pmId),
    @('/api/guests', $guestId), @('/api/properties', $propertyId), @('/api/users', $createdUserId)
)
foreach ($d in $del) {
    if ($d[1]) { Api DELETE "$($d[0])/$($d[1])" $null $T @(204,200) 'Cleanup' "delete $($d[0].Split('/')[-1])" | Out-Null }
}
# delete the registered test users (admin endpoint). May FK-fail if audit logs reference them.
foreach ($uid in @($admin.userId, $finance.userId, $ownerId, $guestUserId, $workerId)) {
    Api DELETE "/api/users/$uid" $null $T @(204,200,404,409,500) 'Cleanup' "delete test user $uid" | Out-Null
}

# ---------------- SUMMARY ----------------
Write-Host "`n========== SUMMARY ==========" -ForegroundColor Cyan
$total = $script:results.Count
$passed = @($script:results | Where-Object { $_.result -eq 'PASS' }).Count
$failed = $total - $passed
Write-Host "TOTAL=$total PASSED=$passed FAILED=$failed" -ForegroundColor Yellow
$httpRows = @($script:results | Where-Object { $_.ms -gt 0 })
if ($httpRows.Count) {
    $avg = [int](($httpRows | Measure-Object ms -Average).Average)
    $max = ($httpRows | Measure-Object ms -Maximum).Maximum
    Write-Host "HTTP calls=$($httpRows.Count) avg=${avg}ms max=${max}ms"
}
Write-Host "`nFAILURES:" -ForegroundColor Red
$script:results | Where-Object { $_.result -eq 'FAIL' } | ForEach-Object { Write-Host (" - [{0}] {1} {2} exp={3} got={4} :: {5}" -f $_.category, $_.method, $_.path, $_.expect, $_.actual, $_.name) }

$out = 'c:\Users\2506580\Downloads\stayease-backend\stayease-backend\qa-tests'
$script:results | ConvertTo-Json -Depth 5 | Out-File "$out\results.json" -Encoding utf8
$script:results | Format-Table seq,result,category,method,path,expect,actual,ms,name -AutoSize | Out-String -Width 240 | Out-File "$out\results.txt" -Encoding utf8
Write-Host "`nSaved results.json and results.txt to qa-tests\" -ForegroundColor Green
