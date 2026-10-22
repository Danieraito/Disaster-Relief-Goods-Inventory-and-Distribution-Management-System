# Disaster Relief Goods Inventory and Distribution Management System

An AI-driven, priority-focused inventory management and distribution system aligned with **UN Sustainable Development Goal 11 (Sustainable Cities and Communities)**.

---

## 📖 Background of the Study

### Target Organizations & Key Stakeholders
* **Global & Primary Partner:** Department of the Interior and Local Government (DILG) / UN-Habitat / UN-Habitat Philippines
* **Government Partners:** Department of Transportation (DOTr), Office of Civil Defense (OCD), National Economic and Development Authority (NEDA)
* **Community Alliances:** Union of Local Authorities of the Philippines (ULAP), United Nations Youth Association of the Philippines (UNYAP)

> **Core Function:** These entities coordinate urban risk reduction, policy implementation, community-level disaster response, and local relief operations across Philippine barangays.

### Overview of the Existing System
The current disaster response pipeline relies on a **hybrid, paper-based operational process**:
1. **Reporting:** Disaster needs are relayed manually from barangays to Local Government Units (LGUs) via paper logs, SMS, or radio.
2. **Inventory Tracking:** Physical counts are recorded manually in paper logbooks and basic spreadsheets at central storage hubs.
3. **Allocation:** Relief supplies are packaged into standardized bundles regardless of specific community urgency or special needs.
4. **Logistics:** Delivery routes and dispatch schedules are assigned manually by logistics officers.

### Operational Bottlenecks & Impact
| Bottleneck | Operational Impact |
| :--- | :--- |
| ⏱️ **Time-Consuming Procedures** | Physical counts and manual data entry create severe dispatch bottlenecks during critical disaster windows. |
| ❌ **Information Loss & Errors** | Paper logs cause frequent tracking errors, duplicate distribution lists, and missing inventory records. |
| 🚚 **Delayed Dispatch & Zero Visibility** | Lack of real-time tracking creates communication breakdowns between LGU staff, donors, and affected communities. |
| ⚠️ **Lack of Priority Allocation** | Critical goods (medicine, clean water) are bundled and shipped alongside low-priority items without urgency sorting. |
| 🚨 **Public Safety Risks & Waste** | Staff waste vital hours cross-checking logs, resulting in delayed aid, expired perishables, and misallocated supplies. |

### Need for Modernization
The disaster relief pipeline requires an automated, modern system to replace paper log processing and dynamically route goods based on real-time community urgency.

#### References & SDGs
* [UN SDGs Goal 11: Teams & Partners](https://philippines.un.org/en/sdgs/11/teams-partners)
* [DOST STI4SDG: Sustainable Cities and Communities](https://sti4sdg.dost.gov.ph/index.php/2024/09/01/11sustainablecitiesandcommunities/)
* [UN Pacific SDGs Goal 11](https://pacific.un.org/en/sdgs/11)
* [Local Government Academy (LGA) Publication](https://cdn.lga.gov.ph/publication/attachments/1590478478.pdf)

---

## 🎯 Project Objectives

1. **Inventory Management System:** Centralize stock tracking for LGU and barangay officials to record, monitor, update, and manage relief goods in real time.
2. **Disaster & Beneficiary Management System:** Track disaster incidents, affected families, individuals, and their specific assistance requirements.
3. **Donation Management System:** Automatically record incoming relief contributions and update available inventory dynamically.
4. **Distribution Management System:** Log the release of goods to beneficiaries and automatically deduct distributed items from inventory.

---

## MVC Backend

All Java source files are in the `LIB` package and the matching `LIB` folder. Classes outside that package, such as a future GUI, should import the classes they use (for example, `import LIB.Inventory;`) or use `import LIB.*;`.

The Java domain classes are the **Model** layer and own business rules such as transaction approval, role permission checks, and stock changes. `UserAccount` is an abstract base class; `AdminAccount`, `BarangayOfficialAccount`, and `EncoderAccount` inherit from it and override permission methods, demonstrating abstraction, inheritance, and runtime polymorphism. `BarangayScoped` is an interface implemented by accounts, inventory, disasters, evacuation centers, donations, and distributions so workspace membership can be checked consistently. The models also demonstrate encapsulation through private state and controlled methods, and composition through transactions containing item lines that reference inventory and batches. Accounts hold a barangay workspace where applicable, an email, active/inactive status, and a salted PBKDF2 password hash. `LoginController` can authenticate against an in-memory account list; SQL-backed account lookup and persistence are still pending.

Feature controllers coordinate calls to the models, while the GUI/View can call the controller methods when it is added. Each `Barangay` is a workspace for its inventory, disasters, evacuation centers, households, and staff. Active accounts of any role can modify disasters in workspaces they can access. `Beneficiaries` represents a household with at least one confirmed `FamilyMember` at registration; household size and vulnerability counts derive from that roster, and a designated member represents the household head when aid is received. A homeless household uses the barangay name as its address. Each member's age and senior/infant/PWD classifications are stored independently and entered based on human verification; the system does not infer classifications from age. Donations and distributions move through `DRAFT`, `PENDING`, and then `APPROVED` or `REJECTED`; only approval changes stock. Donation lines create distinct `InventoryBatch` records with received and optional expiry dates. Opening inventory is represented as an opening batch. Distribution lines can specify the batch to release; if only one batch has stock, it can be inferred, but the model requires an explicit batch when several are available rather than assuming an expiry/FIFO policy. Transaction and account data remain in memory until database persistence is added.

---

## 📦 Expected Output

The primary output is a web-based **Disaster Relief Goods Management System** featuring:
* **Accessible & Intuitive UI:** Tailored for LGU and Barangay Officials, incorporating accessibility features for Persons with Disabilities (PWD) personnel.
* **Core OOP Inventory Computation:** Deterministic algorithms leveraging Object-Oriented Programming (OOP) concepts for exact inventory tracking.
* **AI-Assisted Reasoning Module:** An integrated AI model that analyzes community urgency levels, prioritizes essential goods, and assists in optimized dispatch decisions.

---

## 💡 Significance of the Study

This study advances software engineering and management research by demonstrating how **Artificial Intelligence can be layered onto classic Object-Oriented Programming (OOP) architectures**. Rather than completely replacing existing operational frameworks, this hybrid model tests how AI can enhance decision-making, priority sorting, and resource allocation without adding prohibitive costs or operational complexity.

---

## 👥 Target Beneficiaries

| Stakeholder Group | Expected Value & Outcome |
| :--- | :--- |
| **LGU & Barangay Staff** | Eliminates manual logbooks, reduces paperwork errors, and speeds up dispatch workflows. |
| **Barangay Residents** | Receives urgent, life-saving supplies faster and tailored to immediate community needs. |
| **Donors & NGOs** | Gains full transparency into inventory levels, ensuring aid reaches intended communities without waste. |
| **UN Organizations & DILG** | Obtains reliable data logging supporting SDG 11 urban risk reduction frameworks. |
| **PWD Personnel** | Interacts with an inclusive, accessible system interface designed for diverse operational roles. |
| **Future Researchers** | Serves as a baseline study for combining traditional software architectures with AI-driven logistics. |

---

## Current status: working application

A Java Swing desktop app for barangay officials to receive donations, register affected households and release relief
goods with a full audit trail. The screens follow the 28-frame design in `Untitled.pdf` (Inter font, teal palette, top
navigation, cards and tables).

* **UI:** Java Swing (custom-painted components in `GUI/`, no extra libraries)
* **Logic:** models, controllers and JDBC repositories in `LIB/` (the Views never touch SQL)
* **Database:** MySQL 8 (a hosted database such as Aiven works); also runs on MariaDB 10.5+
* **Java:** 17 or newer

### Run it

1. Create an empty database, for example `disaster_relief_inventory` (character set `utf8mb4`).
2. Tell the app how to connect. Any one of these works:
   * environment variables `DB_URL`, `DB_USER`, `DB_PASSWORD`
   * a `db.properties` file next to `pom.xml` (copy `db.properties.example`; it is git-ignored)
   * nothing: the app shows a "Database connection" window on first start, tests the connection and saves it
3. Start it with `mvn compile exec:java` (or `run.bat` / `run.sh`). In VS Code you can also run `GUI.App`.
4. On a database with no tables the app offers to create them from `database/schema.sql`, then a **first-run setup**
   asks for the barangay name and the first Admin account. Tick *"load illustrative training data"* only on a practice
   database: it adds fictional Barangay Maligaya records (items, donors, two active and two closed disasters, 37
   households, releases, one donation and one release waiting for approval). Demo accounts such as
   `m.delacruz@maligaya.example` (Barangay Official) and `l.garcia@maligaya.example` (Encoder) use the password
   `Maligaya#2026`. Deactivate them before real use.

Existing databases are upgraded automatically at start-up: `LIB/SchemaUpdater` adds the extra columns and tables the
new screens need. It only adds, never changes or deletes data, and is safe to run repeatedly.

### Roles

| | Admin | Barangay Official | Encoder |
|---|---|---|---|
| View all modules | yes | yes | yes |
| Register households, add items, edit details | yes | yes | yes |
| Post donations / confirm releases | yes | yes | **drafts and "submit for approval" only** |
| Adjust stock, archive or delete unused items, close events | yes | yes | no |
| Export reports and audit log | yes | yes | no (read-only reports, own audit activity) |
| Manage users, roles and barangay workspaces | yes | no | no |

An Encoder's donation or release waits as **Pending approval**; an Official or Admin opens it from the history list and
approves (stock changes) or rejects it with a reason. Admins choose which barangay workspace to work in.

### Screens (design frame to class)

| Frame | Screen | Class |
|---|---|---|
| 1 | Sign in | `LoginFrame` |
| 2 | Response dashboard | `DashboardPage` |
| 3-8 | Inventory list, add item, edit item, item detail, adjust stock, delete safely | `InventoryPage`, `ItemFormPage`, `ItemDetailPage`, `AdjustStockPage`, `DeleteItemPage` |
| 9-11 | Disasters, add disaster, event detail | `DisastersPage`, `DisasterFormPage`, `DisasterDetailPage` |
| 12-14 | Beneficiaries, register household, household detail | `BeneficiariesPage`, `RegisterHouseholdPage`, `HouseholdDetailPage` |
| 15-19 | Donations, new donation, review, saved receipt, donors | `DonationsPage`, `DonationFormPage` (steps 1-2), `DonationDetailPage`, `DonorsPage` |
| 20-25 | Distribution, new release, review, insufficient-stock block, duplicate block, saved acknowledgment | `DistributionPage`, `DistributionFormPage` (steps 1-2 and both blocked states), `DistributionDetailPage` |
| 26-28 | Reports, audit logs, user administration | `ReportsPage`, `AuditPage`, `UsersPage` |

Shared look-and-feel code: `Theme` (colours, Inter font), `Kit` / `Inputs` / `Parts` (buttons, pills, cards, alerts,
fields, tabs, tables), `Lay` (layout that lets wrapped text size itself), `Shell` (top bar and navigation) and `Page`
(load, build, rebuild lifecycle; database work runs off the screen thread through `Task`).

### Rules the app enforces

* **Stock only changes through a posted record.** Donations add batches, releases deduct from a chosen batch, and
  "Adjust stock" posts damage / spoilage / corrections. Item edits never touch quantities.
* **Expired goods can never be released.** Availability and low-stock checks use non-expired stock; expired batches stay
  on the shelf (quarantined) until a spoilage adjustment documents their disposal.
* **No duplicate assistance.** Within a disaster, the same recipient (center plus household representative, or
  household) cannot receive the same item twice. Blocked and insufficient-stock attempts are written to the audit log
  (`VAL-yyyy-nnnn`) without moving stock.
* **Households are "assisted" by household releases.** A delivery to an evacuation center records the representative but
  does not mark any household as assisted, as the saved-acknowledgment frame states.
* **Append-only audit log.** Every change records who, when, before and after, and why. Corrections are new entries.
* Reference numbers are sequential and readable: `ITM-001`, `DIS-2026-003`, `HH-2026-0042`, `DON-2026-0088`,
  `DST-2026-0188` (claim receipt `CLM-0188`), `ADJ-...`, `EDIT-...`, `VAL-...`, `DNR-001`.
* Passwords are stored as salted PBKDF2 hashes. Admins issue temporary passwords (shown once); users change their own
  from the top bar.
* Reports export to **Excel (.xlsx)** and **PDF** without any library (`LIB/XlsxWriter`, `LIB/PdfWriter`); receipts can
  be saved as PDF or printed.

### Where it differs from the PDF (on purpose)

* The "Illustrative training workspace / Fictional records only" lines are gone because these are real records.
* Added what a working system needs but the mock-ups do not show: Admin workspace switcher, change password, add
  barangay workspace, add evacuation center, closing an event, archived items, drafts, pending-approval states and a
  household-release mode next to the evacuation-center release.
* The release batch is chosen explicitly (the batch that expires first is suggested), as the project notes require.

### `database/schema.sql` fix

The original script failed on a clean database: a `CHECK` constraint used a column that also had a foreign key with
`ON UPDATE CASCADE`, which MySQL 8 and MariaDB reject. IDs never change here, so every `ON UPDATE CASCADE` was removed.
Nothing else changed. Databases that already exist are untouched.

### Project phases

| Phase | Status |
|---|---|
| 1. Structure (models and controllers) | Done |
| 2. SQL (schema, repositories, audit, approvals, reports) | Done; schema fixed for clean installs |
| 3. GUI (all 28 design frames in Swing) | Done |
| 4. Final testing and debugging | Scripted tests pass (see `tests/`); needs a run against your MySQL / Aiven database and a user-acceptance pass |

### Tested

* Compiled with `--release 17`.
* End to end on MariaDB 10.11 with MySQL-8-style strict SQL mode (`ONLY_FULL_GROUP_BY`, `STRICT_TRANS_TABLES`):
  fresh install, demo data, every report, and walkthroughs that type into the real screens (see `tests/`).
* **Not** tested against Aiven / MySQL itself. Run `tests/FreshInstallTest` against a practice database first.

### Security reminder

`chat.json` (an AI chat export in the original repository) contains database host names and password-looking strings.
Rotate the database password, delete the file and remove it from git history. `.gitignore` now ignores it and
`db.properties`.

### Older notes

The original README is kept in `docs/README-original.md`. `CHAT_CONTEXT_EXPORT.txt` and
`pleasereadROIEMARbeforeproceeding.txt` are the original hand-off notes and describe the earlier project state.
