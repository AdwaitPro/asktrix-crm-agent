-- Asktrix development CRM — schema.
--
-- This stands in for the real Asktrix CRM until it exposes APIs. It implements api/openapi.yaml so
-- the Android app is built against a genuine backend rather than static fixtures.
--
-- THE CRITICAL PROPERTY: this database holds the FULL customer phone number and email, and the API
-- layer never returns them. Masking happens here, server-side, exactly as ADR-0003 requires. That is
-- what makes §4 real — the device is not trusted to mask anything, because it never receives the
-- unmasked value.

DROP TABLE IF EXISTS pii_access_log    CASCADE;
DROP TABLE IF EXISTS location_pings    CASCADE;
DROP TABLE IF EXISTS attendance        CASCADE;
DROP TABLE IF EXISTS rtc_rooms         CASCADE;
DROP TABLE IF EXISTS rtc_signals       CASCADE;
DROP TABLE IF EXISTS call_recordings   CASCADE;
DROP TABLE IF EXISTS call_records      CASCADE;
DROP TABLE IF EXISTS call_sessions     CASCADE;
DROP TABLE IF EXISTS timeline_entries  CASCADE;
DROP TABLE IF EXISTS documents         CASCADE;
DROP TABLE IF EXISTS remarks           CASCADE;
DROP TABLE IF EXISTS clients           CASCADE;
DROP TABLE IF EXISTS idempotency_keys  CASCADE;
DROP TABLE IF EXISTS refresh_tokens    CASCADE;
DROP TABLE IF EXISTS devices           CASCADE;
DROP TABLE IF EXISTS employees         CASCADE;

CREATE TABLE employees (
    employee_id     TEXT PRIMARY KEY,
    employee_code   TEXT UNIQUE NOT NULL,
    display_name    TEXT NOT NULL,
    -- Development server: scrypt-hashed, never plaintext, even here.
    password_hash   TEXT NOT NULL,
    password_salt   TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN (
                        'CUSTOMER_SUPPORT','SALES','DOCUMENTATION',
                        'ACCOUNTS','RELATIONSHIP_MANAGER','TEAM_LEADER')),
    permissions     JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- §10: working hours are enforced here, server-side. The device clock is user-influenced and
    -- this is a compliance boundary.
    work_start      TIME NOT NULL DEFAULT '09:30',
    work_end        TIME NOT NULL DEFAULT '18:30',
    work_days       TEXT[] NOT NULL DEFAULT ARRAY['MON','TUE','WED','THU','FRI','SAT'],
    timezone        TEXT NOT NULL DEFAULT 'Asia/Kolkata',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    is_demo         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Device binding (§14–§20) and the OSP obligation to record the identity of the device used for
-- each call.
CREATE TABLE devices (
    device_id       TEXT PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    manufacturer    TEXT,
    model           TEXT,
    os_version      TEXT,
    app_version     TEXT,
    push_token      TEXT,
    attestation     TEXT,
    compliant       BOOLEAN NOT NULL DEFAULT TRUE,
    last_verdict    TEXT,
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Refresh tokens rotate on every use. Replaying a used token means theft, so the whole family is
-- revoked rather than just the one token.
CREATE TABLE refresh_tokens (
    token_id        TEXT PRIMARY KEY,
    family_id       TEXT NOT NULL,
    employee_id     TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    device_id       TEXT NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);

CREATE TABLE clients (
    client_id           TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    service_id          TEXT,
    -- ---------------------------------------------------------------------------------------
    -- NEVER SERIALISED TO THE DEVICE. The API layer emits mask_phone()/mask_email() output only.
    phone_full          TEXT NOT NULL,
    email_full          TEXT,
    -- ---------------------------------------------------------------------------------------
    assigned_employee   TEXT REFERENCES employees(employee_id) ON DELETE SET NULL,
    process_status      TEXT NOT NULL DEFAULT 'NEW' CHECK (process_status IN (
                            'NEW','DOCUMENTS_PENDING','DOCUMENTS_RECEIVED','CLIENT_NOT_RESPONDING',
                            'PAYMENT_PENDING','PAYMENT_RECEIVED','WAITING_GOVERNMENT_APPROVAL',
                            'CALLBACK_SCHEDULED','COMPLETED')),
    payment_status      TEXT NOT NULL DEFAULT 'NOT_DUE' CHECK (payment_status IN (
                            'NOT_DUE','PENDING','PARTIAL','RECEIVED','REFUNDED')),
    government_status   TEXT NOT NULL DEFAULT 'NOT_APPLICABLE' CHECK (government_status IN (
                            'NOT_APPLICABLE','NOT_SUBMITTED','SUBMITTED','UNDER_REVIEW',
                            'APPROVED','REJECTED')),
    documents_pending   INTEGER NOT NULL DEFAULT 0,
    follow_up_at        TIMESTAMPTZ,
    last_interaction_at TIMESTAMPTZ,
    -- Optimistic concurrency. The app sends the version it saw; a mismatch yields 409 plus the
    -- current state, so the outbox can resolve without a second round trip (§9, §23).
    version             INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Seeded demonstration data. Real activity is written with FALSE, so the console can show
    -- either set without ever deleting anything.
    is_demo BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_clients_assigned ON clients(assigned_employee);
CREATE INDEX idx_clients_status   ON clients(process_status);

CREATE TABLE remarks (
    remark_id   TEXT PRIMARY KEY,
    client_id   TEXT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    author_id   TEXT REFERENCES employees(employee_id) ON DELETE SET NULL,
    author_name TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_remarks_client ON remarks(client_id, created_at DESC);

-- Metadata only. KYC file content is never delivered to the device — the employee needs to know
-- which documents are outstanding, not to read them.
CREATE TABLE documents (
    document_id TEXT PRIMARY KEY,
    client_id   TEXT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    kind        TEXT NOT NULL,
    status      TEXT NOT NULL CHECK (status IN ('PENDING','RECEIVED','VERIFIED','REJECTED')),
    received_at TIMESTAMPTZ
);
CREATE INDEX idx_documents_client ON documents(client_id);

CREATE TABLE timeline_entries (
    entry_id        TEXT PRIMARY KEY,
    client_id       TEXT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN (
                        'CALL','REMARK','STATUS_CHANGE','PAYMENT','DOCUMENT','FOLLOW_UP','EMAIL')),
    summary         TEXT NOT NULL,
    actor_name      TEXT,
    call_record_id  TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Seeded demonstration data. Real activity is written with FALSE, so the console can show
    -- either set without ever deleting anything.
    is_demo BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_timeline_client ON timeline_entries(client_id, occurred_at DESC);

-- §5: a call is requested by clientId. No phone number appears in this table's API projection.
CREATE TABLE call_sessions (
    call_session_id TEXT PRIMARY KEY,
    client_id       TEXT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    employee_id     TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    device_id       TEXT,
    state           TEXT NOT NULL CHECK (state IN (
                        'REQUESTED','RINGING_AGENT','RINGING_CUSTOMER','BRIDGED',
                        'COMPLETED','BUSY','NO_ANSWER','FAILED','CANCELLED')),
    reason          TEXT,
    failure_reason  TEXT,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    connected_at    TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    duration_seconds INTEGER
);
CREATE INDEX idx_sessions_employee ON call_sessions(employee_id, requested_at DESC);

CREATE TABLE call_recordings (
    call_record_id TEXT PRIMARY KEY,
    mime_type      TEXT NOT NULL,
    bytes          BYTEA NOT NULL,
    duration_secs  INTEGER,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE call_records (
    call_record_id   TEXT PRIMARY KEY,
    call_session_id  TEXT REFERENCES call_sessions(call_session_id) ON DELETE SET NULL,
    client_id        TEXT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    employee_id      TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    -- OSP security conditions require the device identity on every call record, IST-synchronised
    -- timestamps, and one-year retention. See docs/research/india-telecom-legal.md.
    device_id        TEXT,
    direction        TEXT NOT NULL CHECK (direction IN ('OUTBOUND','INBOUND','MISSED')),
    state            TEXT NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    -- Recording lives with the telephony provider / CRM. The device never downloads it (§6).
    recording_available BOOLEAN NOT NULL DEFAULT FALSE,
    recording_uri    TEXT,
    -- Seeded demonstration data. Real activity is written with FALSE, so the console can show
    -- either set without ever deleting anything.
    is_demo BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_calls_employee ON call_records(employee_id, started_at DESC);
CREATE INDEX idx_calls_client   ON call_records(client_id, started_at DESC);

CREATE TABLE attendance (
    attendance_id   TEXT PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN ('CHECK_IN','CHECK_OUT')),
    -- Device-reported vs server-received. recorded_at is authoritative for payroll.
    occurred_at     TIMESTAMPTZ NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    accuracy_metres REAL,
    photo_uploaded  BOOLEAN NOT NULL DEFAULT FALSE,
    photo_bytes     BYTEA,
    -- Seeded demonstration data. Real activity is written with FALSE, so the console can show
    -- either set without ever deleting anything.
    is_demo BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_attendance_employee ON attendance(employee_id, occurred_at DESC);

CREATE TABLE location_pings (
    ping_id         BIGSERIAL PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    device_id       TEXT,
    sampled_at      TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    accuracy_metres REAL,
    is_mocked       BOOLEAN NOT NULL DEFAULT FALSE,
    battery_percent INTEGER,
    -- Seeded demonstration data. Real activity is written with FALSE, so the console can show
    -- either set without ever deleting anything.
    is_demo BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_pings_employee ON location_pings(employee_id, sampled_at DESC);

-- Makes the offline outbox safe: replaying a key returns the original result instead of repeating
-- the action (§9, §23).
CREATE TABLE idempotency_keys (
    key             TEXT NOT NULL,
    employee_id     TEXT NOT NULL,
    endpoint        TEXT NOT NULL,
    status_code     INTEGER NOT NULL,
    response_body   JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, employee_id)
);

-- Every time an admin unmasks a customer's real contact details, it is recorded here.
--
-- §4 keeps full details away from employees. A manager may still need them for an escalation, so the
-- admin API exposes one explicit reveal — and this table is what makes that defensible under DPDP:
-- it answers "who looked at this customer's data, when, and why".
CREATE TABLE pii_access_log (
    log_id      BIGSERIAL PRIMARY KEY,
    admin_id    TEXT NOT NULL,
    admin_name  TEXT NOT NULL,
    client_id   TEXT NOT NULL,
    reason      TEXT,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pii_log_client ON pii_access_log(client_id, accessed_at DESC);

-- WebRTC signalling over HTTP (docs/adr/0006-webrtc-calling.md).
--
-- Signalling is a handful of small messages exchanged while a call is being set up, so it does not
-- need a socket. Storing them makes the service deployable on any platform, including serverless,
-- where a long-lived WebSocket cannot be held open. The media itself never touches this server: it
-- flows peer to peer.
CREATE TABLE rtc_rooms (
    room_id         TEXT PRIMARY KEY,
    call_session_id TEXT NOT NULL,
    client_id       TEXT NOT NULL,
    employee_id     TEXT NOT NULL,
    device_id       TEXT,
    -- Single-use secret in the customer's link, so a forwarded link cannot join someone else's call.
    customer_token  TEXT NOT NULL,
    agent_joined    BOOLEAN NOT NULL DEFAULT FALSE,
    customer_joined BOOLEAN NOT NULL DEFAULT FALSE,
    connected_at    TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rtc_signals (
    id         BIGSERIAL PRIMARY KEY,
    room_id    TEXT NOT NULL REFERENCES rtc_rooms(room_id) ON DELETE CASCADE,
    -- Who the message is FOR, so each side polls only its own queue.
    for_role   TEXT NOT NULL CHECK (for_role IN ('agent','customer')),
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rtc_signals_room ON rtc_signals(room_id, for_role, id);
