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

The Java domain classes are the **Model** layer and own business rules such as transaction approval, role permission checks, and stock changes. `UserAccount` is an abstract base class; `AdminAccount`, `BarangayOfficialAccount`, and `EncoderAccount` inherit from it and override permission methods. This demonstrates abstraction, inheritance, and polymorphism. Feature controllers coordinate calls to the models, while the GUI/View can call the controller methods when it is added. The controllers currently work with in-memory objects; database persistence and login authentication are not implemented yet.

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




Project Phases:

### Phase 1: Requirement Analysis and Architecture Planning

| Task Description | Status |
| :--- | :--- |
| Establish the operational foundation for the Disaster Relief Goods Inventory and Distribution Management System. | |
| Finalize technical requirements, roles, and operational workflows. | |
| Structure the project to specifically solve critical LGU/DILG bottlenecks (e.g., manual inventory counting, paper log errors, and dispatch delays). | |
| Align the technical architecture with UN SDG 11 objectives to shift toward urgency-based community relief tracking. | |

### Phase 2: UI/UX Prototyping and Access Control

| Task Description | Status |
| :--- | :--- |
| Transition the 28 planned design screens into the interactive web application interface for BarangayCare. | |
| Construct the system around 10 major pages, establishing a central Dashboard branching out to Inventory, Disasters, Beneficiaries, Donations, Distribution, Reports, Audit Logs, and User Administration. | |
| Implement strict, hierarchical role permissions across the application. | |
| Grant Admin accounts comprehensive control over the workspace and user roles. | |
| Grant Barangay Officials the authority to manage records, confirm relief releases, and export data reports. | |
| Restrict Encoders strictly to data entry and drafting preparation. | |

### Phase 3: Core Application and Database Development

| Task Description | Status |
| :--- | :--- |
| Build the functional backend and interactive frontend relying on traditional Object-Oriented Programming (OOP) concepts. | |
| Design the relational database architecture in MySQL to link disaster events, household records, and donor transactions, ensuring distributed quantities automatically deduct from available inventory. | |
| Develop a responsive React web application frontend utilizing persistent state management and real-time search filtering. | |
| Write the backend logic in Java using structured generic data models to handle incoming relief computations and track multi-variable metrics (e.g., senior citizens, infants, PWDs). | |

### Phase 4: AI Integration, Auditing, and Final Testing

| Task Description | Status |
| :--- | :--- |
| Integrate an AI model to evaluate real-time data and dynamically route goods based on urgency. | |
| Finalize the Audit Logs module to generate a read-only transaction history of all system activities and stock movements for strict accountability. | |
| Conduct comprehensive usability testing to guarantee platform reliability, efficiency, and full accessibility for PWD personnel. | |
| Prepare the completed system for final evaluation and noting by the faculty in-charge. | |