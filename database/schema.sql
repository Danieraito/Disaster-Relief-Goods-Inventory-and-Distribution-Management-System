CREATE DATABASE IF NOT EXISTS disaster_relief_inventory
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE disaster_relief_inventory;

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
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT
);

ALTER TABLE households
    ADD CONSTRAINT fk_household_head_member
    FOREIGN KEY (household_id, household_head_member_id)
    REFERENCES family_members (household_id, member_id)
    ON UPDATE CASCADE ON DELETE RESTRICT;

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
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id)
        REFERENCES donors (donor_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_donation_receiver FOREIGN KEY (received_by_account_id)
        REFERENCES user_accounts (account_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_donation_item_inventory FOREIGN KEY (inventory_item_id, barangay_id)
        REFERENCES inventory_items (inventory_item_id, barangay_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
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
    ) ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_household_disaster FOREIGN KEY (beneficiary_id, disaster_id)
        REFERENCES households (household_id, disaster_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_center FOREIGN KEY (evacuation_center_id, barangay_id)
        REFERENCES evacuation_centers (center_id, barangay_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_releaser FOREIGN KEY (released_by_account_id)
        REFERENCES user_accounts (account_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_item_inventory FOREIGN KEY (inventory_item_id, barangay_id)
        REFERENCES inventory_items (inventory_item_id, barangay_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_distribution_item_batch FOREIGN KEY (batch_id, inventory_item_id)
        REFERENCES inventory_batches (batch_id, inventory_item_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
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
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_audit_donation FOREIGN KEY (donation_id)
        REFERENCES donations (donation_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_audit_distribution FOREIGN KEY (distribution_id)
        REFERENCES distributions (distribution_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Insert a household, at least one family member, and its household-head reference
-- in one JDBC transaction; set the head and commit only after the confirmed roster is complete.
-- Age and senior/infant/PWD flags are independent values entered by a human.
-- Inventory totals should be calculated as SUM(remaining_quantity) across batches.
-- Approvals must update status and all affected batch quantities in one JDBC transaction.
-- Reports are queries over these tables; they do not need a stored reports table.