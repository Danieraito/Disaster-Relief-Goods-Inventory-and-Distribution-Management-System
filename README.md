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

The Java domain classes are the **Model** layer and own business rules such as transaction approval, role permission checks, and stock changes. `UserAccount` is an abstract base class; `AdminAccount`, `BarangayOfficialAccount`, and `EncoderAccount` inherit from it and override permission methods, demonstrating abstraction, inheritance, and runtime polymorphism. `BarangayScoped` is an interface implemented by accounts, inventory, disasters, evacuation centers, donations, and distributions so workspace membership can be checked consistently. The models also demonstrate encapsulation through private state and controlled methods, and composition through transactions containing item lines that reference inventory and batches. Accounts hold a barangay workspace where applicable, an email, active/inactive status, and a salted PBKDF2 password hash. Accounts are saved in SQLite through `UserRepository` (only the salted hash is stored).

Feature controllers coordinate calls to the models, while the GUI/View can call the controller methods when it is added. Each `Barangay` is a workspace for its inventory, disasters, evacuation centers, households, and staff. Active accounts of any role can modify disasters in workspaces they can access. `Beneficiaries` represents a household with at least one confirmed `FamilyMember` at registration; household size and vulnerability counts derive from that roster, and a designated member represents the household head when aid is received. A homeless household uses the barangay name as its address. Each member's age and senior/infant/PWD classifications are stored independently and entered based on human verification; the system does not infer classifications from age. Donations and distributions move through `DRAFT`, `PENDING`, and then `APPROVED` or `REJECTED`; only approval changes stock. Donation lines create distinct `InventoryBatch` records with received and optional expiry dates. Opening inventory is represented as an opening batch. Distribution lines can specify the batch to release; if only one batch has stock, it can be inferred, but the model requires an explicit batch when several are available rather than assuming an expiry/FIFO policy. All records are saved in the SQLite file `barangaycare.db` through the repository classes.

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




## Project Phases

### Phase 1: Structure — Core Backend Structure Done

- Java models and controllers organized in the `LIB` package.
- Core barangay, account, household, disaster, inventory, donation, and distribution relationships established.
- Household roster, stock-batch, and transaction approval rules represented in the models.
- Compiles successfully; database persistence and full workflow integration remain for later phases.

### Phase 2: SQL — Done

- Design the relational schema for barangays, users, households and members, disasters, inventory batches, donations, and distributions.
- Add database connection and persistence.
- Keep transaction status, stock totals, and batch quantities consistent with atomic database updates.
- Persist password hashes and salts securely; do not store plaintext passwords.
- Implement report generation and automatic audit recording after database integration.

### Phase 3: GUI — Done (web pages in `web/`)

- Build the user interface from the planned screens.
- Connect the GUI to the backend controllers.
- Reflect role permissions and barangay workspaces in the available screens and actions.
- Support household registration, member verification, and explicit stock-batch selection when needed.

### Phase 4: Final Testing and Debugging — In progress

- Test the complete GUI-to-database workflows.
- Verify role permissions, barangay access, household records, approvals, and batch-level stock changes.
- Test error handling, audit history, reports, and accessibility.
- Fix remaining issues and prepare the system for final evaluation.

---

## How to run

1. Install Java 11 or newer (JDK, so `javac` is available).
2. Open a terminal in the project folder (the one with `run.bat`).
3. Windows: double-click `run.bat`. Mac/Linux: `sh run.sh`.
4. Open http://localhost:8080 in your browser.

The first start creates `barangaycare.db` from `database/schema.sql` and `database/seed_demo.sql`.
An existing `barangaycare.db` keeps its data; missing columns are added automatically.

**Demo sign-ins** (fictional, change before real use):

| Role | Email | Password |
| :--- | :--- | :--- |
| Admin | admin@barangaycare.local | Admin#2026 |
| Barangay Official | msantos@barangaycare.local | Official#2026 |
| Encoder | lgarcia@barangaycare.local | Encoder#2026 |

Add more accounts on the **User Administration** screen. The temporary password is shown once.

On the login page, pick the same **Role** as the account, and for Officials and Encoders the **Workspace** Barangay Commonwealth.

## Screens and how they connect

| Screen | Page | Server class | What it does |
| :--- | :--- | :--- | :--- |
| Login | `login.html` | `ApiHandler` | Sign in by workspace, role, email and password |
| Dashboard | `dashboard.html` | (reads the others) | Stock alerts, active response progress, pending approvals, recent transactions |
| Inventory | `inventory.html` | `ApiHandler` | Items, batches, expiry and low-stock status; edit details; remove stock; delete unused items |
| Disasters | `disasters.html` | `DisasterApi` | Record, close and reopen disasters; evacuation centers |
| Beneficiaries | `beneficiaries.html` | `HouseholdApi` | Register and edit households (members, head, FACED no.); priority score |
| Donations | `donations.html` | `DonationApi` | Donors, record donations, approve/reject, acknowledgment receipt |
| Distribution | `distribution.html` | `DistributionApi` | Release goods per batch and round, relief packs, bulk release by priority, approve/reject, claim record |
| Reports | `reports.html` | (reads the others) | Inventory, beneficiary, donation and distribution reports; request letter for more goods (to C/MSWDO); CSV and PDF export |
| Audit Logs | `audit.html` | `AuditApi` | Read-only history of every change and blocked attempt |
| User Administration | `users.html` | `UserApi` | Admin only: add users, change roles, deactivate, reset passwords, download database backup |

## Business rules the system enforces

- **Approvals.** Donations and distributions are saved as `PENDING`. Only approval changes stock. Officials and Admins can approve straight away when they record; Encoders' records wait for an Official.
- **One transaction.** Approval, the stock change and the audit row are saved together in one database transaction. If any part fails, nothing is saved. A record can only be approved once.
- **Batches.** Each donation line becomes its own batch (`DON-2026-0001-1`, ...). A release must name the batch; if only one usable batch exists it is picked automatically. There is no automatic FIFO.
- **Expiry.** Expired batches cannot be released and expired goods cannot be received.
- **Removing stock.** On Inventory, Admins and Officials can remove stock from a batch (wrong entry, damaged, expired, lost, recount) with a reason; it is logged with an `ADJ-` reference. An item can be deleted only if it was never used in a donation or distribution.
- **Rounds and duplicates.** Relief is given in rounds (for example a food pack every few days). A household can have only one pending or confirmed release per round of a disaster. Blocked duplicates and releases with too little stock are written to the audit log.
- **Priority.** Each household gets a priority score from `Beneficiaries.priorityScore`: 3 points per senior, PWD, infant or pregnant member; 4 for a totally damaged house (2 for partial); 2 if evacuated; 1 point per 2 members (max 5). Lists and bulk releases put the highest score first.
- **Relief packs.** A pack (e.g. Family Food Pack) lists the goods one family receives. `ReliefPack.familiesCovered` works out how many families the usable stock can serve. A bulk release gives one pack to each ticked household in one transaction.
- **FACED card number** (formerly DAFAC) is optional per household and must be unique within a disaster.
- **Closed disasters** accept no new households or releases until reopened.
- **Workspaces.** Officials and Encoders see and change only their barangay. Admins see all barangays and choose one when creating records.
- **Accounts.** Role and status changes take effect on the person's next click. You cannot deactivate yourself or remove the last active Admin.
- **Audit log** rows cannot be edited or deleted (database triggers).

## Code layout (MVC)

- **Model** (`LIB`): the domain classes (`Donations`, `Distribution`, `Inventory`, `Beneficiaries`, ...) hold the rules. The `*Repository` classes save and load them with SQL.
- **Controller** (`LIB/*Controller` and `SERVER/*Api`): the API classes read the form, call the controllers and models, and save the result.
- **View** (`web/`): HTML pages that call the JSON API (`/api/...`). `web/app.js` has the shared helpers.

List screens and reports read rows with `Database.query(...)`. Every change goes through the model classes.

## How it follows the real relief procedure

1. **Disaster** recorded (Disasters screen).
2. **Registration** of affected families with a verified member list and FACED card number (Beneficiaries).
3. **Validation**: an Official approves records entered by Encoders.
4. **Goods**: donations posted to stock; when stock is short, the **Request for goods** report produces a letter to the City/Municipal Social Welfare and Development Office.
5. **Distribution** in rounds, using relief packs, highest-priority families first (Distribution).
6. **Claim record** printed for the household head to sign; every release is in the audit log.
7. **Reports** to the LGU (Reports).

## Proposal items not built yet

- **AI model** ("AI model for further reasoning", Expected Output). The priority score is the rule-based ("traditional algorithm") part. An AI step would need an online AI service and API key.
- **Delivery routes / logistics** (mentioned in the background as part of the old process).
