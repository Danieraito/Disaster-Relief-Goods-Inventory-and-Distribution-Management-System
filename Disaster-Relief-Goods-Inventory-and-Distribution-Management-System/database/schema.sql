-- Select the target database before running this script. For a hosted service,
-- this should be the database name in DB_URL (for example, defaultdb on Aiven).

CREATE TABLE barangays (
    barangay_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    municipality VARCHAR(150) NOT NULL,
    province VARCHAR(150) NOT NULL,
    UNIQUE KEY uq_barangay_location (name, municipality, province)
);

CREATE TABLE user_accounts (
    account_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    role ENUM('ADMIN', 'BARANGAY_OFFICIAL', 'ENCODER') NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    barangay_id VARCHAR(36) NULL,
    password_salt BINARY(16) NOT NULL,
    password_hash BINARY(32) NOT NULL,
    CONSTRAINT chk_user_barangay_role CHECK (
        (role = 'ADMIN' AND barangay_id IS NULL)
        OR (role <> 'ADMIN' AND barangay_id IS NOT NULL)
    ),
    CONSTRAINT fk_user_barangay FOREIGN KEY (barangay_id)
        REFERENCES barangays (barangay_id)
 ON DELETE RESTRICT
);

CREATE TABLE disasters (
    disaster_id VARCHAR(36) PRIMARY KEY,
    barangay_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(100) NOT NULL,
    location VARCHAR(255) NOT NULL,
    disaster_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    severity VARCHAR(40) NULL,
    affected_areas TEXT NULL,
    UNIQUE KEY uq_disaster_barangay (disaster_id, barangay_id),
    CONSTRAINT fk_disaster_barangay FOREIGN KEY (barangay_id)
        REFERENCES barangays (barangay_id)
 ON DELETE RESTRICT
);

CREATE TABLE evacuation_centers (
    center_id VARCHAR(36) PRIMARY KEY,
    barangay_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    UNIQUE KEY uq_center_barangay (center_id, barangay_id),
    CONSTRAINT chk_center_capacity CHECK (capacity > 0),
    CONSTRAINT fk_center_barangay FOREIGN KEY (barangay_id)
        REFERENCES barangays (barangay_id)
 ON DELETE RESTRICT
);

CREATE TABLE households (
    household_id VARCHAR(36) PRIMARY KEY,
    disaster_id VARCHAR(36) NOT NULL,
    household_name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    assistance_requirements TEXT NULL,
    damage_level VARCHAR(40) NULL,
    household_head_member_id VARCHAR(36) NULL,
    UNIQUE KEY uq_household_disaster (household_id, disaster_id),
    CONSTRAINT fk_household_disaster FOREIGN KEY (disaster_id)
        REFERENCES disasters (disaster_id)
 ON DELETE RESTRICT
);

CREATE TABLE family_members (
    member_id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    age INT NOT NULL,
    is_senior_citizen BOOLEAN NOT NULL DEFAULT FALSE,
    is_infant BOOLEAN NOT NULL DEFAULT FALSE,
    is_pwd BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_member_household (household_id, member_id),
    CONSTRAINT chk_member_age CHECK (age >= 0),
    CONSTRAINT fk_member_household FOREIGN KEY (household_id)
        REFERENCES households (household_id)
 ON DELETE RESTRICT
);

ALTER TABLE households
    ADD CONSTRAINT fk_household_head_member
    FOREIGN KEY (household_id, household_head_member_id)
    REFERENCES family_members (household_id, member_id)
 ON DELETE RESTRICT;

CREATE TABLE inventory_items (
    inventory_item_id VARCHAR(36) PRIMARY KEY,
    barangay_id VARCHAR(36) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    low_stock_threshold INT NOT NULL DEFAULT 0,
    storage_location VARCHAR(255) NULL,
    UNIQUE KEY uq_inventory_item_barangay (inventory_item_id, barangay_id),
    CONSTRAINT chk_inventory_threshold CHECK (low_stock_threshold >= 0),
    CONSTRAINT fk_inventory_barangay FOREIGN KEY (barangay_id)
        REFERENCES barangays (barangay_id)
 ON DELETE RESTRICT
);

CREATE TABLE donors (
    donor_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    donor_type ENUM('INDIVIDUAL', 'ORGANIZATION', 'AGENCY') NOT NULL,
    contact_person VARCHAR(200) NULL,
    contact_details VARCHAR(255) NULL
);

CREATE TABLE donations (
    donation_id VARCHAR(36) PRIMARY KEY,
    barangay_id VARCHAR(36) NOT NULL,
    donor_id VARCHAR(36) NOT NULL,
    donation_date DATE NOT NULL,
    receiving_location VARCHAR(255) NOT NULL,
    delivery_reference VARCHAR(150) NULL,
    received_by_account_id VARCHAR(36) NULL,
    status ENUM('DRAFT', 'PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'DRAFT',
    UNIQUE KEY uq_donation_barangay (donation_id, barangay_id),
    CONSTRAINT fk_donation_barangay FOREIGN KEY (barangay_id)
        REFERENCES barangays (barangay_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id)
        REFERENCES donors (donor_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_donation_receiver FOREIGN KEY (received_by_account_id)
        REFERENCES user_accounts (account_id)
 ON DELETE RESTRICT
);

CREATE TABLE donation_items (
    donation_id VARCHAR(36) NOT NULL,
    barangay_id VARCHAR(36) NOT NULL,
    line_number INT NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    batch_reference VARCHAR(100) NULL,
    expiry_date DATE NULL,
    PRIMARY KEY (donation_id, line_number),
    UNIQUE KEY uq_donation_line_inventory (donation_id, line_number, inventory_item_id),
    CONSTRAINT chk_donation_line_number CHECK (line_number > 0),
    CONSTRAINT chk_donation_quantity CHECK (quantity > 0),
    CONSTRAINT fk_donation_item_donation FOREIGN KEY (donation_id, barangay_id)
        REFERENCES donations (donation_id, barangay_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_donation_item_inventory FOREIGN KEY (inventory_item_id, barangay_id)
        REFERENCES inventory_items (inventory_item_id, barangay_id)
 ON DELETE RESTRICT
);

CREATE TABLE inventory_batches (
    batch_id VARCHAR(100) PRIMARY KEY,
    inventory_item_id VARCHAR(36) NOT NULL,
    batch_type ENUM('OPENING', 'DONATION') NOT NULL,
    donation_id VARCHAR(36) NULL,
    donation_line_number INT NULL,
    received_date DATE NOT NULL,
    expiry_date DATE NULL,
    received_quantity INT NOT NULL,
    remaining_quantity INT NOT NULL,
    UNIQUE KEY uq_batch_item (batch_id, inventory_item_id),
    UNIQUE KEY uq_batch_donation_line (donation_id, donation_line_number),
    CONSTRAINT chk_batch_quantities CHECK (
        received_quantity > 0
        AND remaining_quantity >= 0
        AND remaining_quantity <= received_quantity
    ),
    CONSTRAINT chk_batch_source CHECK (
        (batch_type = 'OPENING' AND donation_id IS NULL AND donation_line_number IS NULL)
        OR (batch_type = 'DONATION' AND donation_id IS NOT NULL AND donation_line_number IS NOT NULL)
    ),
    CONSTRAINT fk_batch_donation_line FOREIGN KEY (
        donation_id, donation_line_number, inventory_item_id
    ) REFERENCES donation_items (
        donation_id, line_number, inventory_item_id
    ) ON DELETE RESTRICT
);

CREATE TABLE distributions (
    distribution_id VARCHAR(36) PRIMARY KEY,
    barangay_id VARCHAR(36) NOT NULL,
    disaster_id VARCHAR(36) NOT NULL,
    beneficiary_id VARCHAR(36) NULL,
    evacuation_center_id VARCHAR(36) NULL,
    distribution_date DATE NOT NULL,
    release_location VARCHAR(255) NULL,
    receipt_reference VARCHAR(150) NOT NULL,
    released_by_account_id VARCHAR(36) NULL,
    status ENUM('DRAFT', 'PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'DRAFT',
    UNIQUE KEY uq_distribution_barangay (distribution_id, barangay_id),
    CONSTRAINT chk_distribution_destination CHECK (
        beneficiary_id IS NOT NULL
        OR evacuation_center_id IS NOT NULL
        OR release_location IS NOT NULL
    ),
    CONSTRAINT fk_distribution_disaster FOREIGN KEY (disaster_id, barangay_id)
        REFERENCES disasters (disaster_id, barangay_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_household_disaster FOREIGN KEY (beneficiary_id, disaster_id)
        REFERENCES households (household_id, disaster_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_center FOREIGN KEY (evacuation_center_id, barangay_id)
        REFERENCES evacuation_centers (center_id, barangay_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_releaser FOREIGN KEY (released_by_account_id)
        REFERENCES user_accounts (account_id)
 ON DELETE RESTRICT
);

CREATE TABLE distribution_items (
    distribution_id VARCHAR(36) NOT NULL,
    barangay_id VARCHAR(36) NOT NULL,
    line_number INT NOT NULL,
    inventory_item_id VARCHAR(36) NOT NULL,
    batch_id VARCHAR(100) NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (distribution_id, line_number),
    CONSTRAINT chk_distribution_line_number CHECK (line_number > 0),
    CONSTRAINT chk_distribution_quantity CHECK (quantity > 0),
    CONSTRAINT fk_distribution_item_distribution FOREIGN KEY (distribution_id, barangay_id)
        REFERENCES distributions (distribution_id, barangay_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_item_inventory FOREIGN KEY (inventory_item_id, barangay_id)
        REFERENCES inventory_items (inventory_item_id, barangay_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_item_batch FOREIGN KEY (batch_id, inventory_item_id)
        REFERENCES inventory_batches (batch_id, inventory_item_id)
 ON DELETE RESTRICT
);

CREATE TABLE audit_logs (
    audit_id VARCHAR(36) PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    action_at DATETIME(6) NOT NULL,
    reference VARCHAR(255) NULL,
    before_value TEXT NULL,
    after_value TEXT NULL,
    reason TEXT NULL,
    account_id VARCHAR(36) NULL,
    donation_id VARCHAR(36) NULL,
    distribution_id VARCHAR(36) NULL,
    CONSTRAINT fk_audit_account FOREIGN KEY (account_id)
        REFERENCES user_accounts (account_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_audit_donation FOREIGN KEY (donation_id)
        REFERENCES donations (donation_id)
 ON DELETE RESTRICT,
    CONSTRAINT fk_audit_distribution FOREIGN KEY (distribution_id)
        REFERENCES distributions (distribution_id)
 ON DELETE RESTRICT
);

-- Database-side workflow guards and read-only summaries carried over from the
-- SQLite design. Run this script with a MySQL client that supports DELIMITER.
DELIMITER //

CREATE TRIGGER donations_start_as_draft
BEFORE INSERT ON donations
FOR EACH ROW
BEGIN
    IF NEW.status <> 'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'New donations must start as DRAFT.';
    END IF;
END//

CREATE TRIGGER donations_status_flow
BEFORE UPDATE ON donations
FOR EACH ROW
BEGIN
    DECLARE line_count INT DEFAULT 0;
    DECLARE approver_count INT DEFAULT 0;

    IF NOT (
        OLD.status = NEW.status
        OR (OLD.status = 'DRAFT' AND NEW.status = 'PENDING')
        OR (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED', 'REJECTED'))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid donation status transition.';
    END IF;

    IF OLD.status = 'DRAFT' AND NEW.status = 'PENDING' THEN
        SELECT COUNT(*) INTO line_count
        FROM donation_items WHERE donation_id = OLD.donation_id;
        IF line_count = 0 OR NEW.received_by_account_id IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A pending donation needs item lines and no approver yet.';
        END IF;
    END IF;

    IF OLD.status = 'PENDING' AND NEW.status = 'APPROVED' THEN
        SELECT COUNT(*) INTO line_count
        FROM donation_items WHERE donation_id = OLD.donation_id;
        SELECT COUNT(*) INTO approver_count
        FROM user_accounts
        WHERE account_id = NEW.received_by_account_id
          AND status = 'ACTIVE'
          AND (role = 'ADMIN' OR (role = 'BARANGAY_OFFICIAL' AND barangay_id = NEW.barangay_id));
        IF line_count = 0 OR approver_count = 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donation approval requires an active authorized approver and item lines.';
        END IF;
    END IF;
END//

CREATE TRIGGER distributions_start_as_draft
BEFORE INSERT ON distributions
FOR EACH ROW
BEGIN
    IF NEW.status <> 'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'New distributions must start as DRAFT.';
    END IF;
END//

CREATE TRIGGER distributions_status_flow
BEFORE UPDATE ON distributions
FOR EACH ROW
BEGIN
    DECLARE line_count INT DEFAULT 0;
    DECLARE missing_batch_count INT DEFAULT 0;
    DECLARE approver_count INT DEFAULT 0;
    DECLARE household_head_count INT DEFAULT 0;

    IF NOT (
        OLD.status = NEW.status
        OR (OLD.status = 'DRAFT' AND NEW.status = 'PENDING')
        OR (OLD.status = 'PENDING' AND NEW.status IN ('APPROVED', 'REJECTED'))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid distribution status transition.';
    END IF;

    IF NEW.status = 'PENDING' AND OLD.status = 'DRAFT' THEN
        SELECT COUNT(*) INTO line_count
        FROM distribution_items WHERE distribution_id = OLD.distribution_id;
        IF line_count = 0 OR NEW.released_by_account_id IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A pending distribution needs item lines and no approver yet.';
        END IF;
        IF NEW.beneficiary_id IS NOT NULL THEN
            SELECT COUNT(*) INTO household_head_count
            FROM households
            WHERE household_id = NEW.beneficiary_id
              AND household_head_member_id IS NOT NULL;
            IF household_head_count = 0 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A household must have a designated head before aid is issued.';
            END IF;
        END IF;
    END IF;

    IF OLD.status = 'PENDING' AND NEW.status = 'APPROVED' THEN
        SELECT COUNT(*) INTO line_count
        FROM distribution_items WHERE distribution_id = OLD.distribution_id;
        SELECT COUNT(*) INTO missing_batch_count
        FROM distribution_items
        WHERE distribution_id = OLD.distribution_id AND batch_id IS NULL;
        SELECT COUNT(*) INTO approver_count
        FROM user_accounts
        WHERE account_id = NEW.released_by_account_id
          AND status = 'ACTIVE'
          AND (role = 'ADMIN' OR (role = 'BARANGAY_OFFICIAL' AND barangay_id = NEW.barangay_id));
        IF line_count = 0 OR missing_batch_count > 0 OR approver_count = 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Distribution approval requires item lines, selected batches, and an active authorized approver.';
        END IF;
        IF NEW.beneficiary_id IS NOT NULL THEN
            SELECT COUNT(*) INTO household_head_count
            FROM households
            WHERE household_id = NEW.beneficiary_id
              AND household_head_member_id IS NOT NULL;
            IF household_head_count = 0 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A household must have a designated head before aid is issued.';
            END IF;
        END IF;
    END IF;
END//

CREATE TRIGGER donation_items_insert_draft_only
BEFORE INSERT ON donation_items
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM donations WHERE donation_id = NEW.donation_id AND status = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donation lines can only be added to draft donations.';
    END IF;
END//

CREATE TRIGGER donation_items_update_draft_only
BEFORE UPDATE ON donation_items
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM donations WHERE donation_id = OLD.donation_id AND status = 'DRAFT')
       OR NOT EXISTS (SELECT 1 FROM donations WHERE donation_id = NEW.donation_id AND status = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donation lines can only be changed on draft donations.';
    END IF;
END//

CREATE TRIGGER donation_items_delete_draft_only
BEFORE DELETE ON donation_items
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM donations WHERE donation_id = OLD.donation_id AND status = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donation lines can only be deleted from draft donations.';
    END IF;
END//

CREATE TRIGGER distribution_items_insert_draft_only
BEFORE INSERT ON distribution_items
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM distributions WHERE distribution_id = NEW.distribution_id AND status = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Distribution lines can only be added to draft distributions.';
    END IF;
END//

CREATE TRIGGER distribution_items_update_draft_only
BEFORE UPDATE ON distribution_items
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM distributions WHERE distribution_id = OLD.distribution_id AND status = 'DRAFT')
       OR NOT EXISTS (SELECT 1 FROM distributions WHERE distribution_id = NEW.distribution_id AND status = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Distribution lines can only be changed on draft distributions.';
    END IF;
END//

CREATE TRIGGER distribution_items_delete_draft_only
BEFORE DELETE ON distribution_items
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM distributions WHERE distribution_id = OLD.distribution_id AND status = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Distribution lines can only be deleted from draft distributions.';
    END IF;
END//

CREATE TRIGGER donation_batches_require_approval
BEFORE INSERT ON inventory_batches
FOR EACH ROW
BEGIN
    IF NEW.batch_type = 'DONATION'
       AND NOT EXISTS (SELECT 1 FROM donations WHERE donation_id = NEW.donation_id AND status = 'APPROVED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Donation batches can only be created for approved donations.';
    END IF;
END//

CREATE TRIGGER audit_logs_no_update
BEFORE UPDATE ON audit_logs
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit logs cannot be modified.';
END//

CREATE TRIGGER audit_logs_no_delete
BEFORE DELETE ON audit_logs
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit logs cannot be deleted.';
END//

DELIMITER ;

CREATE OR REPLACE VIEW v_inventory_stock AS
SELECT item.inventory_item_id,
       item.barangay_id,
       item.item_name,
       item.category,
       item.unit,
       COALESCE(stock.quantity, 0) AS quantity,
       item.low_stock_threshold,
       CASE WHEN COALESCE(stock.quantity, 0) <= item.low_stock_threshold THEN TRUE ELSE FALSE END AS is_low_stock,
       stock.earliest_expiry
FROM inventory_items AS item
LEFT JOIN (
    SELECT inventory_item_id,
           SUM(remaining_quantity) AS quantity,
           MIN(CASE WHEN remaining_quantity > 0 THEN expiry_date END) AS earliest_expiry
    FROM inventory_batches
    GROUP BY inventory_item_id
) AS stock ON stock.inventory_item_id = item.inventory_item_id;

CREATE OR REPLACE VIEW v_household_summary AS
SELECT household.household_id,
       household.household_name,
       household.address,
       household.disaster_id,
       disaster.barangay_id,
       COALESCE(member_counts.household_size, 0) AS household_size,
       COALESCE(member_counts.senior_count, 0) AS senior_count,
       COALESCE(member_counts.infant_count, 0) AS infant_count,
       COALESCE(member_counts.pwd_count, 0) AS pwd_count
FROM households AS household
JOIN disasters AS disaster ON disaster.disaster_id = household.disaster_id
LEFT JOIN (
    SELECT household_id,
           COUNT(*) AS household_size,
           SUM(is_senior_citizen) AS senior_count,
           SUM(is_infant) AS infant_count,
           SUM(is_pwd) AS pwd_count
    FROM family_members
    GROUP BY household_id
) AS member_counts ON member_counts.household_id = household.household_id;

CREATE OR REPLACE VIEW v_donation_summary AS
SELECT donation.donation_id,
       donation.barangay_id,
       donation.donation_date,
       donation.status,
       donor.name AS donor,
       COALESCE(item_totals.total_quantity, 0) AS total_quantity
FROM donations AS donation
JOIN donors AS donor ON donor.donor_id = donation.donor_id
LEFT JOIN (
    SELECT donation_id, SUM(quantity) AS total_quantity
    FROM donation_items
    GROUP BY donation_id
) AS item_totals ON item_totals.donation_id = donation.donation_id;

CREATE OR REPLACE VIEW v_distribution_summary AS
SELECT distribution.distribution_id,
       distribution.barangay_id,
       distribution.distribution_date,
       distribution.status,
       household.household_name AS household,
       distribution.disaster_id,
       COALESCE(item_totals.total_quantity, 0) AS total_quantity
FROM distributions AS distribution
LEFT JOIN households AS household ON household.household_id = distribution.beneficiary_id
LEFT JOIN (
    SELECT distribution_id, SUM(quantity) AS total_quantity
    FROM distribution_items
    GROUP BY distribution_id
) AS item_totals ON item_totals.distribution_id = distribution.distribution_id;

-- Insert a household, at least one family member, and its household-head reference
-- in one JDBC transaction; set the head and commit only after the confirmed roster is complete.
-- Age and senior/infant/PWD flags are independent values entered by a human.
-- Inventory totals should be calculated as SUM(remaining_quantity) across batches.
-- Approvals must update status and all affected batch quantities in one JDBC transaction.
-- Reports are queries over these tables; they do not need a stored reports table.