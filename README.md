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




## Project Phases

### Phase 1: Structure — Core Backend Structure Done

- Java models and controllers organized in the `LIB` package.
- Core barangay, account, household, disaster, inventory, donation, and distribution relationships established.
- Household roster, stock-batch, and transaction approval rules represented in the models.
- Compiles successfully; database persistence and full workflow integration remain for later phases.

### Phase 2: SQL — Schema Drafted; Persistence Not Started

- Maven configuration includes MySQL Connector/J; no database server is required to compile the models.
- `LIB.DatabaseConnection` reads `DB_URL` (optional; defaults to the local `disaster_relief_inventory` database), `DB_USER`, and `DB_PASSWORD` from environment variables.
- To run the JDBC smoke test from the repository root after starting MySQL and setting those variables, use `mvn exec:java`. It runs `LIB.DatabaseConnectionCheck`, which tests the connection with `SELECT 1`.
- The initial MySQL schema is in `database/schema.sql`; review it against the final screen fields before applying it to a server.
- Add and verify a database server, then test the JDBC connection and implement persistence.
- Keep transaction status, stock totals, and batch quantities consistent with atomic database updates.
- Persist password hashes and salts securely; do not store plaintext passwords.
- Implement report generation and automatic audit recording after database integration.

### Phase 3: GUI — Not Started

- Build the user interface from the planned screens.
- Connect the GUI to the backend controllers.
- Reflect role permissions and barangay workspaces in the available screens and actions.
- Support household registration, member verification, and explicit stock-batch selection when needed.

### Phase 4: Final Testing and Debugging — Not Started

- Test the complete GUI-to-database workflows.
- Verify role permissions, barangay access, household records, approvals, and batch-level stock changes.
- Test error handling, audit history, reports, and accessibility.
- Fix remaining issues and prepare the system for final evaluation.
