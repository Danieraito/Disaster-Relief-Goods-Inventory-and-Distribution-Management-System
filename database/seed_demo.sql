-- Fictional demo data for testing (matches the placeholder-data note in the Lab Activity).
-- User accounts are NOT seeded here: passwords must be hashed by the Java code (PBKDF2).
PRAGMA foreign_keys = ON;

INSERT INTO barangays VALUES ('BRG-001','Barangay Commonwealth','Quezon City','Metro Manila');

INSERT INTO disasters VALUES ('DIS-001','BRG-001','Typhoon Demo','Typhoon','Barangay Commonwealth',
  '2026-10-01','ONGOING','HIGH','Purok 1, Purok 2');
INSERT INTO evacuation_centers VALUES ('EC-001','BRG-001','Commonwealth Elementary School','Commonwealth Ave.',300);

INSERT INTO households VALUES ('HH-001','DIS-001','Dela Cruz Household','Purok 1, Barangay Commonwealth','Food, water, hygiene kits','PARTIAL');
INSERT INTO family_members VALUES ('FM-001','HH-001','Juan Dela Cruz',42,0,0,0,1);
INSERT INTO family_members VALUES ('FM-002','HH-001','Lola Maria Dela Cruz',71,1,0,0,0);
INSERT INTO family_members VALUES ('FM-003','HH-001','Baby Ana Dela Cruz',1,0,1,0,0);

INSERT INTO inventory_items VALUES ('ITM-001','BRG-001','Rice (25kg sack)','Food','sack',10,'Warehouse A');
INSERT INTO inventory_items VALUES ('ITM-002','BRG-001','Bottled Water (1L)','Water','bottle',100,'Warehouse A');
INSERT INTO inventory_batches VALUES ('B-001','ITM-001','2026-09-20','2027-03-01',50,50);
INSERT INTO inventory_batches VALUES ('B-002','ITM-002','2026-09-25','2027-09-25',500,500);

INSERT INTO donors VALUES ('DON-001','Sample Foundation','ORGANIZATION','Ms. Reyes','0917-000-0000');
