@echo off
rem ============================================================================
rem  StayEase - start every backend service in one go.
rem
rem  Replaces the five terminals described in HOW-TO-RUN.md section 1. Each
rem  service still gets its OWN window, so you can read its log and stop just
rem  that one with Ctrl+C; this script only launches them in the right order.
rem
rem  Usage (from anywhere):   backend\run-all.cmd
rem
rem  Order matters for exactly one reason: eureka-server must be accepting
rem  connections before the others register, so we WAIT for port 8761 and then
rem  start the remaining four together (they are independent at startup - the
rem  monolith only needs property-service at request time, not boot time).
rem
rem  MySQL credentials: set DB_USERNAME / DB_PASSWORD before running and every
rem  window inherits them, e.g.
rem      set DB_PASSWORD=my-password
rem      backend\run-all.cmd
rem ============================================================================

setlocal

rem Anchor every path to this script's own folder, so it works no matter what
rem the current directory is. %~dp0 already ends in a backslash.
set "ROOT=%~dp0"

rem NOTE ON ".\mvnw.cmd": the leading .\ is required, not stylistic. `start /D`
rem puts each window in the right service folder, but many managed Windows
rem builds set NoDefaultCurrentDirectoryInExePath=1, which stops cmd resolving a
rem command from the current directory - a bare `mvnw.cmd` then dies with
rem "'mvnw.cmd' is not recognized". An explicit relative path bypasses that and
rem still survives a repo checked out under a path containing spaces.

echo.
echo === StayEase: starting all backend services ===
echo.

rem --- Pre-flight: MySQL. All three data services fail to boot without it, and
rem     a Flyway/connection stack trace in five windows at once is a miserable
rem     way to find that out. Warn, but don't block: the DB may be remote.
call :isListening 3306
if not errorlevel 1 goto :mysqlOk
echo [WARN] Nothing is listening on localhost:3306 - is MySQL running?
echo        notification-service, property-service and stayease-backend need
echo        it. Continuing anyway.
echo.
:mysqlOk

rem --- 1. Service registry. Everything else registers with it. ---
call :isListening 8761
if not errorlevel 1 goto :eurekaAlreadyUp

echo [1/5] eureka-server        :8761  starting...
start "StayEase eureka-server" /D "%ROOT%eureka-server" cmd /k .\mvnw.cmd spring-boot:run
echo       waiting for the registry to accept connections...
call :waitFor 8761 90
if errorlevel 1 goto :eurekaFailed
echo       registry is up.
goto :startTheRest

:eurekaAlreadyUp
echo [1/5] eureka-server        :8761  already up - leaving it alone.

:startTheRest

rem --- 2-5. The rest, launched together. ---
echo [2/5] notification-service :8082  starting...
start "StayEase notification-service" /D "%ROOT%notification-service" cmd /k .\mvnw.cmd spring-boot:run

echo [3/5] property-service     :8001  starting...
start "StayEase property-service" /D "%ROOT%property-service" cmd /k .\mvnw.cmd spring-boot:run

echo [4/5] stayease-backend     :8085  starting...
start "StayEase stayease-backend" /D "%ROOT%stayease-backend" cmd /k .\mvnw.cmd spring-boot:run

echo [5/5] api-gateway          :7000  starting...
start "StayEase api-gateway" /D "%ROOT%api-gateway" cmd /k .\mvnw.cmd spring-boot:run

echo.
echo All five launched, each in its own window titled "StayEase ...".
echo.
echo Give Eureka ~30-60s to settle, then check the dashboard: you should see
echo EUREKA-SERVER, API-GATEWAY, PROPERTY-SERVICE, NOTIFICATION-SERVICE and
echo STAYEASE-BACKEND.
echo.
echo   Eureka dashboard  http://localhost:8761
echo   API gateway       http://localhost:7000
echo   Monolith Swagger  http://localhost:8085/swagger-ui.html
echo.
echo To stop a service: Ctrl+C in its window (or just close it).
echo.
goto :end

:eurekaFailed
echo.
echo [ERROR] eureka-server did not come up within ~3 minutes. Check its window
echo         for the failure, then re-run this script. Nothing else was started,
echo         because without the registry the other services can't find one
echo         another.
goto :end

rem ---------------------------------------------------------------------------
rem  Subroutines. These live OUTSIDE any ( ) block on purpose: a parenthesised
rem  block is parsed in one go, so a %var% inside it never sees an update made
rem  in the same block, and a goto would jump straight out of it. Loops with a
rem  counter therefore have to be written as plain, unnested lines.
rem ---------------------------------------------------------------------------

rem  waitFor <port> <maxTries>  - poll every 2s until something listens there.
rem  Polling beats a fixed sleep: a cold Maven run takes far longer than a warm
rem  one, so any hard-coded guess is either too short or pure waste.
rem  Returns errorlevel 0 once it's up, 1 on timeout.
:waitFor
set "WAIT_PORT=%~1"
set "WAIT_MAX=%~2"
set /a WAIT_TRIES=0
:waitForLoop
call :isListening %WAIT_PORT%
if not errorlevel 1 exit /b 0
set /a WAIT_TRIES+=1
if %WAIT_TRIES% GEQ %WAIT_MAX% exit /b 1
timeout /t 2 /nobreak >nul
goto :waitForLoop

rem  isListening <port>  - errorlevel 0 when something is LISTENING on it.
rem  Both filters are needed: the first narrows to the port, the second drops
rem  outbound connections so we only report a real server socket. The leading
rem  colon stops ":8761" matching an ephemeral port such as 58761, and the
rem  trailing space keeps it off the remote-address column.
:isListening
netstat -an | findstr /c:":%~1 " | findstr /c:"LISTENING" >nul 2>&1
exit /b %errorlevel%

:end
endlocal
