-- BarangayCare: Disaster Relief Goods Inventory and Distribution Management System
-- Phase 2 schema (SQLite). Mirrors the Java models in package LIB.
-- Dates are stored as ISO text (YYYY-MM-DD); timestamps as YYYY-MM-DD HH:MM:SS.

PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------- workspaces
CREATE TABLE barangays (
    barangay_id   TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    municipality  TEXT NOT NULL,
    province      TEXT NOT NULL
);

-- ------------------------------------------------------------------ accounts
-- Admins have no barangay (they can access all); other roles must have one.
-- Only the salted PBKDF2 hash is stored, never the plain password.
CREATE TABLE users (
    account_id     TEXT PRIMARY KEY,
    name           TEXT NOT NULL,
    username       TEXT NOT NULL UNIQUE,
    email          TEXT NOT NULL UNIQUE,
    role           TEXT NOT NULL CHECK (role IN ('ADMIN','BARANGAY_OFFICIAL','ENCODER')),
    barangay_id    TEXT REFERENCES barangays(barangay_id),
    password_salt  BLOB NOT NULL,
    password_hash  BLOB NOT NULL,
    status         TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    CHECK ((role = 'ADMIN' AND barangay_id IS NULL)
        OR (role <> 'ADMIN' AND barangay_id IS NOT NULL))
);

-- ---------------------------------------------------------------- disasters
CREATE TABLE disasters (
    disaster_id    TEXT PRIMARY KEY,
    barangay_id    TEXT NOT NULL REFERENCES barangays(barangay_id),
    name           TEXT NOT NULL,
    type           TEXT NOT NULL,
    location       TEXT NOT NULL,
    disaster_date  TEXT NOT NULL,
    status         TEXT NOT NULL,
    severity       TEXT,
    affected_areas TEXT
);

CREATE TABLE evacuation_centers (
    center_id    TEXT PRIMARY KEY,
    barangay_id  TEXT NOT NULL REFERENCES barangays(barangay_id),
    name         TEXT NOT NULL,
    address      TEXT NOT NULL,
    capacity     INTEGER NOT NULL CHECK (capacity >= 0)
);

-- ------------------------------------------------- households (Beneficiaries)
CREATE TABLE households (
    beneficiary_id          TEXT PRIMARY KEY,
    disaster_id             TEXT NOT NULL REFERENCES disasters(disaster_id),
    name                    TEXT NOT NULL,
    address                 TEXT NOT NULL,      -- barangay name if homeless
    assistance_requirements TEXT,
    damage_level            TEXT
);

-- Age and the senior/infant/PWD flags are entered separately (human-verified).
CREATE TABLE family_members (
    member_id    TEXT PRIMARY KEY,
    household_id TEXT NOT NULL REFERENCES households(beneficiary_id) ON DELETE CASCADE,
    full_name    TEXT NOT NULL,
    age          INTEGER NOT NULL CHECK (age >= 0),
    is_senior    INTEGER NOT NULL DEFAULT 0 CHECK (is_senior IN (0,1)),
    is_infant    INTEGER NOT NULL DEFAULT 0 CHECK (is_infant IN (0,1)),
    is_pwd       INTEGER NOT NULL DEFAULT 0 CHECK (is_pwd IN (0,1)),
    is_head      INTEGER NOT NULL DEFAULT 0 CHECK (is_head IN (0,1))
);
-- At most one household head per household.
CREATE UNIQUE INDEX one_head_per_household ON family_members(household_id) WHERE is_head = 1;

-- ---------------------------------------------------------------- inventory
-- Item total quantity is NOT stored; it is the sum of batch remaining quantities
-- (see view v_inventory_stock), so it can never disagree with the batches.
CREATE TABLE inventory_items (
    item_id             TEXT PRIMARY KEY,
    barangay_id         TEXT NOT NULL REFERENCES barangays(barangay_id),
    item_name           TEXT NOT NULL,
    category            TEXT,
    unit                TEXT NOT NULL,
    low_stock_threshold INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
    storage_location    TEXT
);

CREATE TABLE inventory_batches (
    batch_id           TEXT PRIMARY KEY,
    item_id            TEXT NOT NULL REFERENCES inventory_items(item_id),
    received_date      TEXT NOT NULL,
    expiry_date        TEXT,
    received_quantity  INTEGER NOT NULL CHECK (received_quantity > 0),
    remaining_quantity INTEGER NOT NULL,
    CHECK (remaining_quantity >= 0 AND remaining_quantity <= received_quantity)
);

-- ---------------------------------------------------------------- donations
CREATE TABLE donors (
    donor_id        TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    type            TEXT NOT NULL,
    contact_person  TEXT,
    contact_details TEXT
);

CREATE TABLE donations (
    donation_id        TEXT PRIMARY KEY,
    barangay_id        TEXT NOT NULL REFERENCES barangays(barangay_id),
    donor_id           TEXT NOT NULL REFERENCES donors(donor_id),
    donation_date      TEXT NOT NULL,
    receiving_location TEXT,
    delivery_reference TEXT,
    status             TEXT NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
    received_by        TEXT REFERENCES users(account_id)   -- set on approval
);

CREATE TABLE donation_items (
    line_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    donation_id     TEXT NOT NULL REFERENCES donations(donation_id) ON DELETE CASCADE,
    item_id         TEXT NOT NULL REFERENCES inventory_items(item_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    batch_reference TEXT,        -- becomes inventory_batches.batch_id on approval
    expiry_date     TEXT
);

-- ------------------------------------------------------------ distributions
CREATE TABLE distributions (
    distribution_id   TEXT PRIMARY KEY,
    barangay_id       TEXT NOT NULL REFERENCES barangays(barangay_id),
    disaster_id       TEXT NOT NULL REFERENCES disasters(disaster_id),
    household_id      TEXT NOT NULL REFERENCES households(beneficiary_id),
    center_id         TEXT REFERENCES evacuation_centers(center_id),
    distribution_date TEXT NOT NULL,
    release_location  TEXT,
    receipt_reference TEXT,
    status            TEXT NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
    released_by       TEXT REFERENCES users(account_id)    -- set on approval
);

-- A distribution line must name the exact batch it releases from.
CREATE TABLE distribution_items (
    line_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    distribution_id TEXT NOT NULL REFERENCES distributions(distribution_id) ON DELETE CASCADE,
    item_id         TEXT NOT NULL REFERENCES inventory_items(item_id),
    batch_id        TEXT NOT NULL REFERENCES inventory_batches(batch_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0)
);

-- --------------------------------------------------------------- audit logs
CREATE TABLE audit_logs (
    audit_id        TEXT PRIMARY KEY,
    action          TEXT NOT NULL,
    logged_at       TEXT NOT NULL DEFAULT (datetime('now')),
    user_id         TEXT REFERENCES users(account_id),
    donation_id     TEXT REFERENCES donations(donation_id),
    distribution_id TEXT REFERENCES distributions(distribution_id),
    reference       TEXT,
    before_value    TEXT,
    after_value     TEXT,
    reason          TEXT
);

-- ----------------------------------------------------------------- triggers
-- Audit logs are append-only.
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_logs
BEGIN SELECT RAISE(ABORT, 'Audit logs cannot be modified.'); END;
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_logs
BEGIN SELECT RAISE(ABORT, 'Audit logs cannot be deleted.'); END;

-- Status can only move DRAFT -> PENDING -> APPROVED/REJECTED.
CREATE TRIGGER donation_status_flow BEFORE UPDATE OF status ON donations
WHEN NOT ((OLD.status = NEW.status)
       OR (OLD.status = 'DRAFT'   AND NEW.status = 'PENDING')
       OR (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED','REJECTED')))
BEGIN SELECT RAISE(ABORT, 'Invalid donation status change.'); END;

CREATE TRIGGER distribution_status_flow BEFORE UPDATE OF status ON distributions
WHEN NOT ((OLD.status = NEW.status)
       OR (OLD.status = 'DRAFT'   AND NEW.status = 'PENDING')
       OR (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED','REJECTED')))
BEGIN SELECT RAISE(ABORT, 'Invalid distribution status change.'); END;

-- A distribution line may only use a batch of the same item.
CREATE TRIGGER distribution_line_batch_matches_item BEFORE INSERT ON distribution_items
WHEN (SELECT item_id FROM inventory_batches WHERE batch_id = NEW.batch_id) IS NOT NEW.item_id
BEGIN SELECT RAISE(ABORT, 'Batch does not belong to the selected item.'); END;

-- ------------------------------------------------- views (used by Reports)
CREATE VIEW v_inventory_stock AS
SELECT i.item_id, i.barangay_id, i.item_name, i.category, i.unit,
       COALESCE(SUM(b.remaining_quantity), 0) AS quantity,
       i.low_stock_threshold,
       CASE WHEN COALESCE(SUM(b.remaining_quantity), 0) <= i.low_stock_threshold
            THEN 1 ELSE 0 END AS is_low_stock,
       MIN(CASE WHEN b.remaining_quantity > 0 THEN b.expiry_date END) AS earliest_expiry
FROM inventory_items i
LEFT JOIN inventory_batches b ON b.item_id = i.item_id
GROUP BY i.item_id;

CREATE VIEW v_household_summary AS
SELECT h.beneficiary_id, h.name, h.address, h.disaster_id, d.barangay_id,
       COUNT(m.member_id)            AS household_size,
       COALESCE(SUM(m.is_senior), 0) AS senior_count,
       COALESCE(SUM(m.is_infant), 0) AS infant_count,
       COALESCE(SUM(m.is_pwd), 0)    AS pwd_count
FROM households h
JOIN disasters d ON d.disaster_id = h.disaster_id
LEFT JOIN family_members m ON m.household_id = h.beneficiary_id
GROUP BY h.beneficiary_id;

CREATE VIEW v_donation_summary AS
SELECT d.donation_id, d.barangay_id, d.donation_date, d.status, dn.name AS donor,
       COALESCE(SUM(di.quantity), 0) AS total_quantity
FROM donations d
JOIN donors dn ON dn.donor_id = d.donor_id
LEFT JOIN donation_items di ON di.donation_id = d.donation_id
GROUP BY d.donation_id;

CREATE VIEW v_distribution_summary AS
SELECT x.distribution_id, x.barangay_id, x.distribution_date, x.status,
       h.name AS household, x.disaster_id,
       COALESCE(SUM(xi.quantity), 0) AS total_quantity
FROM distributions x
JOIN households h ON h.beneficiary_id = x.household_id
LEFT JOIN distribution_items xi ON xi.distribution_id = x.distribution_id
GROUP BY x.distribution_id;
