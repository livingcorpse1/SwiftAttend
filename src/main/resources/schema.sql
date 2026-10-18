CREATE TABLE IF NOT EXISTS app_user (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(12) NOT NULL CHECK(role IN ('TEACHER','STUDENT')),
 enrollment_number VARCHAR(40) UNIQUE, department VARCHAR(120) NOT NULL DEFAULT 'Computer Science & Engineering', batch VARCHAR(60),
 face_descriptor TEXT, refresh_token_hash VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS batch VARCHAR(60);
CREATE TABLE IF NOT EXISTS subject (
 id UUID PRIMARY KEY, teacher_id UUID NOT NULL REFERENCES app_user(id), code VARCHAR(15) NOT NULL,
 name VARCHAR(120) NOT NULL, department VARCHAR(120) NOT NULL DEFAULT 'Computer Science & Engineering',
 UNIQUE(teacher_id,code)
);
CREATE TABLE IF NOT EXISTS class_schedule (
 id UUID PRIMARY KEY, subject_id UUID NOT NULL REFERENCES subject(id), teacher_id UUID NOT NULL REFERENCES app_user(id),
 day_of_week SMALLINT NOT NULL CHECK(day_of_week BETWEEN 0 AND 6), start_time TIME NOT NULL, end_time TIME NOT NULL,
 room_number VARCHAR(50) NOT NULL, batch VARCHAR(60) NOT NULL DEFAULT 'CS-2026-A', class_type VARCHAR(30) NOT NULL DEFAULT 'Lecture',
 CHECK(start_time < end_time)
);
CREATE INDEX IF NOT EXISTS ix_schedule_teacher_day ON class_schedule(teacher_id,day_of_week);
CREATE TABLE IF NOT EXISTS attendance_session (
 id UUID PRIMARY KEY, teacher_id UUID NOT NULL REFERENCES app_user(id), subject_id UUID NOT NULL REFERENCES subject(id),
 subject_code VARCHAR(15) NOT NULL, subject_name VARCHAR(120) NOT NULL, room_number VARCHAR(50) NOT NULL,
 batch VARCHAR(60) NOT NULL, schedule_id UUID REFERENCES class_schedule(id), secret_key BYTEA NOT NULL,
 status VARCHAR(10) NOT NULL CHECK(status IN ('ACTIVE','ENDED')), started_at TIMESTAMPTZ NOT NULL DEFAULT now(), ended_at TIMESTAMPTZ,
 total_attended INTEGER NOT NULL DEFAULT 0, latitude DOUBLE PRECISION, longitude DOUBLE PRECISION,
 geofence_radius_meters DOUBLE PRECISION NOT NULL DEFAULT 30
);
CREATE INDEX IF NOT EXISTS ix_session_teacher_status ON attendance_session(teacher_id,status);
CREATE INDEX IF NOT EXISTS ix_session_status_started ON attendance_session(status,started_at DESC);
CREATE TABLE IF NOT EXISTS attendance_record (
 id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES attendance_session(id), student_id UUID NOT NULL REFERENCES app_user(id),
 student_name VARCHAR(100) NOT NULL, enrollment_number VARCHAR(40), department VARCHAR(120) NOT NULL,
 subject_id UUID NOT NULL REFERENCES subject(id), verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 verification_method VARCHAR(24) NOT NULL, status VARCHAR(12) NOT NULL CHECK(status IN ('PRESENT','LATE','EXCUSED','ABSENT')),
 nonce VARCHAR(40) NOT NULL, latency_ms INTEGER NOT NULL, similarity_score DOUBLE PRECISION NOT NULL,
 ip_address VARCHAR(64), user_agent VARCHAR(300), is_manual_override BOOLEAN NOT NULL DEFAULT false,
 override_reason VARCHAR(300), overridden_by UUID REFERENCES app_user(id), overridden_at TIMESTAMPTZ,
 UNIQUE(session_id,student_id)
);
CREATE INDEX IF NOT EXISTS ix_record_student_date ON attendance_record(student_id,verified_at DESC);
CREATE INDEX IF NOT EXISTS ix_record_session_date ON attendance_record(session_id,verified_at DESC);
CREATE TABLE IF NOT EXISTS used_qr_nonce (
 session_id UUID NOT NULL REFERENCES attendance_session(id), student_id UUID NOT NULL REFERENCES app_user(id), nonce VARCHAR(40) NOT NULL,
 used_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(session_id,student_id,nonce)
);
CREATE INDEX IF NOT EXISTS ix_nonce_time ON used_qr_nonce(used_at);
CREATE TABLE IF NOT EXISTS audit_log (
 id UUID PRIMARY KEY, teacher_id UUID NOT NULL REFERENCES app_user(id), student_id UUID NOT NULL REFERENCES app_user(id),
 session_id UUID NOT NULL REFERENCES attendance_session(id), old_status VARCHAR(12) NOT NULL, new_status VARCHAR(12) NOT NULL,
 reason VARCHAR(300) NOT NULL, notes VARCHAR(1000), ip_address VARCHAR(64), user_agent VARCHAR(300), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_audit_session_time ON audit_log(session_id,created_at DESC);
CREATE TABLE IF NOT EXISTS security_event (
 id UUID PRIMARY KEY, actor_id UUID REFERENCES app_user(id), session_id UUID REFERENCES attendance_session(id),
 event_type VARCHAR(40) NOT NULL, detail VARCHAR(300) NOT NULL, ip_address VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_security_session_time ON security_event(session_id,created_at DESC);
