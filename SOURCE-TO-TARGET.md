# Aproxy → SwiftAttend source-to-target review

Source reviewed from the clean `main` checkout at `Aproxy-reference/`. The migration is on branch `swiftattend-migration`.

| Aproxy feature / observed source behavior | SwiftAttend equivalent | Implemented | Checked |
|---|---|---:|---:|
| Student and teacher roles; no admin | `STUDENT` / `TEACHER` checks through Spring Security; no invented admin role | Yes | Build; API workflows need PostgreSQL |
| Register/login/logout, BCrypt, JWT access/refresh, protected routes | Spring Security, BCrypt, 15-minute JWT, rotating opaque 7-day HttpOnly refresh cookie, logout revocation | Yes | Compile/test; HTTP workflow needs PostgreSQL |
| Mongoose `User`, `Subject`, `ClassSchedule`, `AttendanceSession`, `AttendanceRecord`, `AuditLog` | PostgreSQL tables, FKs, checks, indexes, transactions | Yes | Schema not run against DB here |
| 1-second WebSocket QR heartbeat; session rooms; QR start/end/reconnect | Native Spring WebSocket, one-use session ticket, scheduled one-second pulse and exponential client reconnect | Yes | Compile only; socket integration unavailable |
| QR HMAC-SHA256 over session ID, millisecond timestamp and nonce | Per-session random 256-bit key and HMAC-SHA256 over the same fields; constant-time compare | Yes | Unit test |
| Aproxy QR allows ≤2,000 ms age and ≤1,000 ms future skew | Same configured limits | Yes | Unit test |
| Source nonce cache keyed by student + nonce (memory-only) | PostgreSQL primary key `(session_id, student_id, nonce)`, retention cleanup | Yes | Database test unavailable |
| Aproxy single attendance record per student/session | PostgreSQL unique `(session_id, student_id)` | Yes | Schema review; concurrency test unavailable |
| Browser face-api.js detection, 128D descriptor and distance threshold 0.45 | Same upstream model assets and library; local inference; server performs Euclidean check | Yes | Source assets copied; camera/browser test unavailable |
| Aproxy active liveness challenge, with a 30-frame pass-through and one-blink shortcut present in source | Random 2-blink or head-turn-and-return challenge; no time/frame auto-pass | Yes | JavaScript syntax only; physical liveness not hardware-tested |
| Source QR verification trusts client `biometricMatched` (default true), auto-passes missing enrollment, offers fake Quick Pass/manual fallback | Require enrolled descriptor and face match; no student Quick Pass or fallback button | Yes | Source behavior confirmed; endpoint testing needs DB |
| Source session has only a radius; no stored classroom coordinates and no actual location decision | Teacher captures a GPS anchor at session start; server checks student coordinates with Haversine | Added | Math unit test; GPS/browser test unavailable |
| Teacher start/end, active session, roster, student scan/history | Session endpoints, teacher dashboard, QR scanner, roster and student history | Yes | Build/syntax; database/browser workflows not run |
| Timetable conflict checks and active schedule | PostgreSQL timetable endpoints with teacher/room/batch overlap checks, current-slot lookup, and teacher schedule screen | Yes | Build; database workflow not run |
| Manual attendance override with mandatory reason and audit record | Teacher-only upsert with reason, status, user agent/IP and audit table | Yes | Build; database workflow not run |
| PDF and CSV session reports; audit log; analytics | PDFBox and CSV endpoints; audit/security event listing; summary analytics endpoint | Yes | Build; DB/report outputs not run |
| Teacher pages for history, analytics, settings; student statistics page | Session history, analytics, audit/security, editable account settings, and timetable views; student history/profile and record-count/subject statistics | Equivalent core views | UI source review; APIs need database |
| Socket.IO `/attendance-session`, `join_session`, `start_session`, `force_rotate`, `end_session`, `student_verified`, `attendance_overridden` | Authenticated Spring WebSocket per active session; periodic QR and roster/override/ended event JSON messages | Equivalent events | Build only |
| Student and teacher UX visual identity | New SwiftAttend landing, logo, responsive colors and dashboard style | Yes | Static source review |
| Actual performance / 30–50 simultaneous users | No invented performance claim; load script supplied | Not measured | Blocked by unavailable PostgreSQL/Docker service |

## Known migration gaps

- Student statistics are based on recorded check-ins and deliberately do not show an attendance percentage because this schema does not contain a class roster denominator or student-to-batch membership.
- The source's face and liveness checks are browser-side. SwiftAttend requires the expected client fields and checks server-side vectors/location, but a modified client can still forge browser-produced claims. This is a limitation of the upstream trust model, not a guarantee that an untrusted browser is attested.
- No server-side facial image/embedding inference is implemented: the source specifically performs `@vladmandic/face-api` locally and transmits only a 128D descriptor. The Java backend validates and compares that descriptor, preserving that privacy boundary.
- The source QR nonce is consumed before later face checks. SwiftAttend inserts nonce and attendance in the same transaction so a face/location rejection does not burn a scan token; replay and duplicate attendance still fail.
