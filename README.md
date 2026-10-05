# SwiftAttend

**Fast. Verified. Fair.** SwiftAttend is a classroom attendance application that combines a rapidly rotating signed QR code with a browser-side face template, an active camera challenge, and a classroom-radius location check.

This Java implementation is maintained as a standalone project. The original Aproxy source was kept in a separate reference checkout during analysis.

## Problem and objectives

Static QR attendance codes can be shared with absent students. SwiftAttend makes a code short-lived and signed, records each student once per session, matches a live browser-computed face descriptor against an enrolled descriptor, and checks browser geolocation against the classroom anchor. Following Aproxy’s actual student flow, the student completes the live camera challenge while GPS is acquired in parallel, then scans the current QR and receives an attendance result. This keeps the 2-second QR window usable.

## Features

- Student and teacher registration, login, role-gated endpoints, BCrypt passwords, 15-minute JWT access tokens, rotating seven-day HttpOnly refresh cookies, and logout revocation.
- Teacher-started sessions with a classroom GPS anchor, adjustable geofence radius, a signed dynamic QR, QR display, live roster, manual attendance changes with a reason and audit record, CSV and PDF exports.
- One-second QR generation through the authenticated `/attendance-session` replacement at `/ws/attendance/{sessionId}`; each code expires after 2 seconds and is HMAC-SHA256 signed with a per-session 256-bit secret.
- Database-backed, per-student nonce replay checks and a unique `(session_id, student_id)` attendance constraint. Transactions make the nonce and attendance insert atomic; older nonce rows are purged after ten minutes.
- Browser-side face-api.js inference using the Aproxy model files. One face must be present, the student performs a random two-blink or head-turn-and-return challenge, and the resulting 128D descriptor is compared on the server with Euclidean distance `≤ 0.45`. Raw video frames are not uploaded.
- Browser geolocation checked server-side with the Haversine distance calculation and a default 100 m radius (teacher-adjustable from 10–1,000 m).
- Realtime roster updates over authenticated native WebSockets with expiring, single-use, session-bound handshake tickets and exponential reconnect.
- Class schedule create/list/active endpoints with conflict checking, teacher analytics, security-event records, audit-log access, CSV/PDF attendance reports.
- Responsive student/teacher screens, QR scanning through the back camera, attendance history and summary stats, face enrollment, teacher account settings, and a fresh SwiftAttend logo, favicon, palette, typography, landing page, and copy.

## Source analysis and migration decisions

The source inventory and source-to-target checklist are in [SOURCE-TO-TARGET.md](SOURCE-TO-TARGET.md). Aproxy’s checked-in code is React 18 + TypeScript + Vite, Express + TypeScript, MongoDB/Mongoose, and Socket.IO. It has **teacher and student roles only**; it has no admin role. The app contains student overview/scan/face ID/stats pages; teacher overview/live sessions/roster/history/analytics/timetable/settings pages; schedules, audit overrides, session exports, JWT refresh, and live attendance.

The checked-in source does not fully match its README claims. Its liveness engine can pass after 30 continuous frames without completing the selected action; `BLINK_TWICE` passes after one blink. Its biometric modal offers a fabricated Quick Pass, permits an un-enrolled descriptor to pass, and exposes a fallback route. The verify API accepts client-supplied biometric booleans and defaults them to true. Session documents contain a radius but no classroom coordinates, and the verification controller does not enforce a geofence. SwiftAttend removes the fabricated pass and fallback, requires an enrolled template, requires an active challenge before scanning, enforces GPS distance, and logs rejected scans. See the checklist for precise behavior and migration notes.

## Architecture

```text
Browser: HTML5 + CSS3 + ES6 JavaScript
  face-api.js runs locally; camera frames remain in the browser
  REST: JSON + bearer JWT; refresh token is an HttpOnly cookie
  WebSocket: session-bound one-use ticket; one-second QR pulses and roster events
             │
             ▼
Java 17 + Spring Boot
  Spring Security · BCrypt · HMAC-SHA256 · validation · geofence · PDF/CSV
             │
             ▼
PostgreSQL
  foreign keys · unique constraints · transactions · lookup indexes
```

Face detection, landmark extraction, blink counting, head pose, and descriptor generation happen in JavaScript with the Aproxy `@vladmandic/face-api` model assets. Java verifies the descriptor against the enrolled template; it never accepts a fabricated Quick Pass or an un-enrolled user as a match. As in the source design, camera challenge completion and geolocation originate in the browser. They are useful anti-proxy checks but do not provide hardware-backed attestation: a hostile client that replaces the browser code can forge client-produced face descriptors, liveness flags, or location readings. A high-assurance deployment would need a trusted kiosk/mobile client or another independent presence signal.

## Attendance flow

1. Teacher signs in and starts a session. The browser obtains the room anchor from `navigator.geolocation`; the server stores it with a configured radius and creates a random 256-bit session secret.
2. The teacher dashboard opens a session WebSocket. Every second the server creates a fresh 96-bit nonce, millisecond timestamp, and HMAC over `sessionId:timestamp:nonce`; the browser renders each signed heartbeat as a PNG.
3. Student signs in and completes a random two-blink or head-turn-and-return challenge. The browser concurrently obtains geolocation and computes the 128-value face descriptor locally.
4. The student scans the current QR. The server checks session state, signature, timestamp (at most 2,000 ms old, at most 1,000 ms in the future), enrolled-face distance, and the classroom radius. It inserts a unique student/nonce row and attendance record in one transaction.
5. A successful insert broadcasts the roster update. A second attendance record or reused nonce is rejected by PostgreSQL uniqueness, even under concurrent requests.

## API

`/api/health` is public. Other routes require an access token except login, registration, and refresh.

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create student or teacher |
| POST | `/api/auth/login` | Public | Login, issue access token and refresh cookie |
| POST | `/api/auth/refresh` | Refresh cookie | Rotate refresh and access tokens |
| POST | `/api/auth/logout` | Authenticated | Revoke refresh token |
| GET | `/api/auth/me` | Authenticated | Current user profile |
| PUT | `/api/auth/me` | Authenticated | Update display name and department |
| POST | `/api/auth/face-descriptor` | Authenticated | Save a 128D browser template |
| POST | `/api/auth/ws-ticket` | Authenticated | Issue a one-time session-bound WebSocket ticket |
| POST | `/api/attendance/session/start` | Teacher | Start a class and set its geofence |
| POST | `/api/attendance/session/end` | Teacher | End an owned active session |
| GET | `/api/attendance/session/active` | Authenticated | Find the current session |
| GET | `/api/attendance/session/{id}/qr.png` | Owning teacher | Return a freshly signed QR image |
| GET | `/api/attendance/session/{id}/roster` | Owning teacher | Fetch the live roster |
| GET | `/api/attendance/sessions` | Teacher | Recent sessions and report links |
| POST | `/api/attendance/verify-qr` | Student | Verify QR, face, liveness declaration, location; mark once |
| GET | `/api/attendance/history/student` | Student | Attendance history |
| POST | `/api/attendance/override` | Owning teacher | Reason-coded manual adjustment with audit |
| GET | `/api/attendance/audit-logs` | Teacher | Audit trail |
| GET | `/api/attendance/security-events` | Teacher | Rejected-scan and security events |
| GET | `/api/attendance/analytics/overview` | Teacher | Session and attendance totals |
| GET | `/api/attendance/reports/session/{id}/csv` | Owning teacher | Session CSV |
| GET | `/api/attendance/reports/session/{id}/pdf` | Owning teacher | Session PDF |
| GET/POST | `/api/timetable`, `/api/timetable/active` | Authenticated / teacher write | Schedule list, active class, conflict-checked create |
| WS | `/ws/attendance/{sessionId}?ticket=…` | One-use ticket | QR pulse and roster events |

## Database

The schema is in `src/main/resources/schema.sql`. It contains `app_user`, `subject`, `class_schedule`, `attendance_session`, `attendance_record`, `used_qr_nonce`, `audit_log`, and `security_event`. Foreign keys, role/status checks, schedule range checks, unique login/enrollment values, and unique attendance/nonce keys are enforced in PostgreSQL. Schema creation is idempotent for local development; production deployments should replace `schema.sql` startup initialization with versioned migrations before evolving the schema.

## Run locally

Requirements: Docker Compose, Java 17+ (the included container image uses Java 17), and a random JWT secret.

```powershell
Copy-Item .env.example .env
# Edit .env and set DATABASE_PASSWORD and JWT_SECRET (32+ random bytes)
docker compose up --build
```

Open <http://localhost:8080>. Browser camera and geolocation require HTTPS in production; localhost is treated as a secure context by modern browsers. For direct Maven use, configure PostgreSQL, set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `JWT_SECRET`, then run `mvn spring-boot:run`. `COOKIE_SECURE` must be `true` behind production HTTPS. PostgreSQL schema is initialized at application startup.

### Use the Supabase PostgreSQL database

The checked-in `.env.example` is prefilled with this project's Supabase session-pooler host, database, and username. Copy it to `.env`, replace `DATABASE_PASSWORD` with the database password from **Project Settings → Database**, and set `JWT_SECRET` to a random value of at least 32 bytes. Keep `.env` private; it is ignored by Git. From PowerShell, run `.\run-supabase.ps1`. This reads `.env` into the current process and starts Spring Boot against Supabase using SSL. Supabase does not use the anon or service-role key for this JDBC connection. The app initializes its tables from `schema.sql` at startup, so connect with the database owner credentials. Do not use `docker compose up` for this mode; Compose starts a separate local PostgreSQL container.

## Testing and measured performance

Run unit checks with `mvn test`. They exercise HMAC payload binding, the age/future-skew boundary, face-distance threshold behavior, and inside/outside geofence calculations. `load-test.ps1` prepares and submits simultaneous student attendance requests against a running local application; it reports request latency and failures and is intended for 30–50 simulated accounts.

The reference install and builds were verified: root and server `npm ci` succeeded, the Aproxy backend TypeScript build passed, and the Aproxy frontend Vite build passed (with its >500 kB chunk warning). The original Aproxy backend and frontend started and each returned HTTP 200 for its health/landing endpoint, with MongoDB unavailable and the backend in its documented memory fallback. All six included source test scripts printed success; the session/attendance scripts keep a process timer alive after their assertions, so those two were stopped after their success output. SwiftAttend’s Java package compiled and its four unit tests passed; its JavaScript syntax check passed. PostgreSQL/Docker is unavailable in this environment, so database-backed workflows, camera hardware behavior, 30–50-user load, and end-to-end timings have **not** been measured here. No latency or throughput figures are claimed.

## Future improvements

- Replace client-declared liveness and browser GPS with a trusted client/device attestation approach for institutions requiring stronger assurance.
- Add schema versioning, institutional identity providers, configurable class rosters, student-to-batch membership, and richer attendance thresholds.
- Add trusted-client/device attestation, richer student attendance percentages once class roster membership is configured, and institution-specific policy settings.
- Run PostgreSQL-backed integration tests, camera/browser acceptance tests, and the included 30–50-client load profile on a provisioned host; record p50/p95 timings before classroom deployment.
