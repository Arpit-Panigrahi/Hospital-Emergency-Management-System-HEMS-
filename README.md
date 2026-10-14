# Hospital Emergency Management System (HEMS)

A robust, multi-tier database application developed in **Java** interfacing directly with **Oracle Database 11g Enterprise** via JDBC (Type 4 Driver). The system features both an **interactive terminal console (CLI)** and a **modern browser-based dashboard (Web UI)**, providing real-time patient triage, dynamic bed allocation, and strict ACID transaction management with automated rollback handling.

---

## 📑 Table of Contents
1. [Architecture & System Flow](#-architecture--system-flow)
2. [Project Structure](#-project-structure)
3. [Database Design & Schema Implementation](#-database-design--schema-implementation)
4. [Key Features & Transaction Handling](#-key-features--transaction-handling)
5. [Prerequisites](#-prerequisites)
6. [Step-by-Step Execution Guide](#-step-by-step-execution-guide)
   - [Option A: Running the Web Dashboard](#option-a-running-the-web-dashboard)
   - [Option B: Running the Console Interface](#option-b-running-the-console-interface)
   - [Option C: Direct Oracle Verification in SQL*Plus](#option-c-direct-oracle-verification-in-sqlplus)
7. [Testing & Demonstration Scenarios](#-testing--demonstration-scenarios)
8. [Git Version Control & Repository](#-git-version-control--repository)

---

## 🏗 Architecture & System Flow

The application implements a multi-tier client-server architecture:

```
+-------------------------------------------------------------------------+
|                              FRONTEND LAYER                             |
|    Browser Web Dashboard (HTML5/CSS3/JS)   OR   Console Interface (CLI) |
+-------------------------------------------------------------------------+
                                    │
                                    ▼ (HTTP REST API / Direct Method Calls)
+-------------------------------------------------------------------------+
|                        BACKEND & SERVICE LAYER                          |
|   WebServer (HTTP Server) / EmergencyService (ACID Transaction Manager) |
+-------------------------------------------------------------------------+
                                    │
                                    ▼ (PreparedStatement / Transaction Boundaries)
+-------------------------------------------------------------------------+
|                        DATA ACCESS LAYER (DAO)                          |
|         PatientDAO / DBConnection (Oracle JDBC Type 4 Driver)          |
+-------------------------------------------------------------------------+
                                    │
                                    ▼ (TCP/IP Port 1521 / TNS)
+-------------------------------------------------------------------------+
|                             DATABASE LAYER                              |
|                Oracle Database 11g Enterprise Edition (orcl)            |
+-------------------------------------------------------------------------+
```

### Execution Pipeline:
1. **Application Authentication**: User credentials submitted at the interface are validated against the `APP_USERS` table via a parameterized JDBC query.
2. **Atomic Triage & Bed Allocation**: Grouped operations execute under `setAutoCommit(false)`. If either patient intake or bed assignment fails, a complete `rollback()` is triggered.
3. **Real-Time Synchronization**: Status transitions (`ADMITTED`, `DISCHARGED`) dynamically update patient health states and free hospital resources.

---

## 📂 Project Structure

All application components are placed in a flat, clean folder structure:

```
C:\Users\lab1\HEMS\
├── DBConnection.java       # Centralized JDBC connection factory (Oracle 11g)
├── PatientDAO.java         # Data Access Object (CRUD, queries, JSON serialization)
├── EmergencyService.java   # Business logic & atomic ACID transaction management
├── MainApp.java            # Interactive command-line interface (CLI)
├── WebServer.java          # Built-in lightweight HTTP REST server (Port 8080)
├── index.html              # Responsive single-page application (HTML5/CSS3/JS)
├── schema.sql              # Oracle DDL & seed data script
├── ojdbc6.jar              # Oracle JDBC Type 4 Driver
├── .gitignore              # Ignores compiled bytecode (*.class)
└── README.md               # Comprehensive project documentation
```

---

## 🗄 Database Design & Schema Implementation

The relational database is built on Oracle Database 11g and enforces entity constraints, check validations, and foreign keys.

### Entity Relationship & Tables

```sql
-- 1. Application Users Table (Authentication)
CREATE TABLE APP_USERS (
    USER_ID       NUMBER(5) PRIMARY KEY,
    USERNAME      VARCHAR2(30) UNIQUE NOT NULL,
    PASSWORD_HASH VARCHAR2(50) NOT NULL,
    FULL_NAME     VARCHAR2(50) NOT NULL,
    ROLE          VARCHAR2(20) NOT NULL CHECK (ROLE IN ('ADMIN', 'DOCTOR', 'NURSE'))
);

-- 2. Emergency Patient Table (Triage & Intake)
CREATE TABLE EMERGENCY_PATIENT (
    PATIENT_ID     NUMBER(10) PRIMARY KEY,
    NAME           VARCHAR2(50) NOT NULL,
    AGE            NUMBER(3) NOT NULL,
    GENDER         VARCHAR2(10),
    TRIAGE_LEVEL   VARCHAR2(15) CHECK (TRIAGE_LEVEL IN ('CRITICAL', 'URGENT', 'NON-URGENT')),
    ADMISSION_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    STATUS         VARCHAR2(20) DEFAULT 'ADMITTED' CHECK (STATUS IN ('ADMITTED', 'DISCHARGED', 'TRANSFERRED'))
);

-- 3. Bed Allocation Table (Resource Management)
CREATE TABLE BED_ALLOCATION (
    BED_ID       NUMBER(5) PRIMARY KEY,
    BED_TYPE     VARCHAR2(20) CHECK (BED_TYPE IN ('ICU', 'ER_BAY', 'TRAUMA')),
    IS_OCCUPIED  CHAR(1) DEFAULT 'N' CHECK (IS_OCCUPIED IN ('Y', 'N')),
    PATIENT_ID   NUMBER(10),
    CONSTRAINT FK_BED_PATIENT FOREIGN KEY (PATIENT_ID) REFERENCES EMERGENCY_PATIENT(PATIENT_ID)
);

-- 4. Emergency Staff Table (Staffing Roster)
CREATE TABLE EMERGENCY_STAFF (
    STAFF_ID     NUMBER(5) PRIMARY KEY,
    NAME         VARCHAR2(50) NOT NULL,
    ROLE         VARCHAR2(20) CHECK (ROLE IN ('DOCTOR', 'NURSE', 'SURGEON')),
    AVAILABILITY CHAR(1) DEFAULT 'Y' CHECK (AVAILABILITY IN ('Y', 'N'))
);
```

### Pre-Seeded Application Accounts
| Username | Password | Role | Description |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | `ADMIN` | Emergency Room Administrator |
| `doctor` | `doctor123` | `DOCTOR` | Attending ER Physician |
| `25bce5654` | `25bce5654` | `ADMIN` | Lab Student Account |

---

## ⚡ Key Features & Transaction Handling

### 1. SQL Injection Prevention
All database queries utilize `PreparedStatement` with typed binding variables (`?`), separating SQL execution plans from user inputs.

### 2. ACID Transaction Control
In `EmergencyService.java`, multi-table changes execute atomically:
```java
Connection con = DBConnection.getConnection();
con.setAutoCommit(false); // Begin multi-statement transaction

// 1. Insert patient record
psPatient.executeUpdate();

// 2. Assign bed only if unoccupied
int bedUpdated = psBed.executeUpdate();
if (bedUpdated == 0) {
    throw new SQLException("Bed is already occupied or does not exist.");
}

con.commit(); // Persist changes permanently upon full success
```
If an exception occurs or the bed is already occupied, `con.rollback()` is executed, preventing orphaned patient records without beds.

---

## 🛠 Prerequisites

- **Java Development Kit (JDK)**: Version 17 or higher (tested with Java 24).
- **Oracle Database**: Oracle Database 11g/12c/19c/XE accessible on network.
- **Git**: Installed for version control.
- **Web Browser**: Chrome, Edge, Firefox, or Safari (for Web UI).
- **Environment Variables (required for backend runtime)**:
  - `HEMS_DB_USER`
  - `HEMS_DB_PASSWORD`
  - Optional: `HEMS_DB_URL` (defaults to `jdbc:oracle:thin:@172.16.8.54:1521:orcl`)
  - Optional: `CORS_ALLOWED_ORIGINS` (comma-separated frontend origins, e.g. `https://<user>.github.io,https://<project>.vercel.app`)

---

## 🚀 Step-by-Step Execution Guide

### Compilation (If source code is modified)
Open PowerShell or Command Prompt in the project folder:
```powershell
cd C:\Users\lab1\HEMS
javac -cp ojdbc6.jar *.java
```

---

### Option A: Running the Web Dashboard

1. Start the Java HTTP REST Server:
```powershell
cd C:\Users\lab1\HEMS
set HEMS_DB_USER=your_db_user
set HEMS_DB_PASSWORD=your_db_password
java -cp ".;ojdbc6.jar" WebServer
```
2. The application will start on port `8080` and automatically open in your default browser at:
   ```
   http://localhost:8080
   ```
3. **Log In**: Use username `admin` and password `admin123`.
4. **Features Available**:
   - **Live Metrics**: Real-time counter of admitted patients, critical triage alerts, and available bed capacity.
   - **Patients List**: Table with triage severity color-badges (`CRITICAL`, `URGENT`, `NON-URGENT`) and instant Discharge actions.
   - **Bed & ICU Matrix**: Visual cards highlighting bed status (Green for Available, Red for Occupied).
   - **Admit Patient Form**: Clean intake form with available bed dropdowns executing atomic admissions.

---

### Option B: Running the Console Interface

1. Launch the interactive CLI:
```powershell
cd C:\Users\lab1\HEMS
java -cp ".;ojdbc6.jar" MainApp
```
2. **Authenticate**: Enter your username and password when prompted.
3. **Navigate the Menu**:
   - `1`: View active emergency patients.
   - `2`: Admit patient & allocate bed (atomic transaction).
   - `3`: View real-time bed availability.
   - `4`: Update patient status (`ADMITTED`, `DISCHARGED`, `TRANSFERRED`).
   - `5`: Discharge patient and release allocated bed.
   - `6`: Exit system.

---

### Option C: Direct Oracle Verification in SQL*Plus

Verify raw table data at any point using SQL*Plus in a separate terminal:
```powershell
sqlplus 25bce5654/25bce5654@orcl
```
Execute SQL inspection queries:
```sql
SELECT * FROM EMERGENCY_PATIENT;
SELECT * FROM BED_ALLOCATION;
SELECT * FROM APP_USERS;
```

---

## 🧪 Testing & Demonstration Scenarios

### Scenario 1: Successful Atomic Admission & Bed Assignment
1. Open the intake form (or CLI option `2`).
2. Admit Patient ID `201`, Name `John Doe`, Age `45`, Gender `M`, Triage `CRITICAL`, Bed `101`.
3. **Observation**:
   - Patient `201` is inserted into `EMERGENCY_PATIENT`.
   - Bed `101` in `BED_ALLOCATION` is updated to `IS_OCCUPIED = 'Y'` and associated with Patient `201`.
   - `con.commit()` confirms the transaction in Oracle.

### Scenario 2: Transaction Rollback on Resource Conflict
1. Attempt to admit a second patient (Patient ID `202`, Name `Jane Smith`) into Bed `101` (already occupied).
2. **Observation**:
   - The conditional SQL query (`WHERE BED_ID = 101 AND IS_OCCUPIED = 'N'`) affects `0` rows.
   - The system triggers an `SQLException` and calls `con.rollback()`.
   - Verifying the database shows that Patient `202` was **not saved**, proving zero orphaned records.

### Scenario 3: Atomic Patient Discharge & Bed Release
1. Trigger discharge for Patient `201` (via the UI Discharge button or CLI option `5`).
2. **Observation**:
   - Patient `201` status changes to `DISCHARGED`.
   - Bed `101` status resets to `IS_OCCUPIED = 'N'` and `PATIENT_ID = NULL`.
   - Bed becomes immediately available for new emergency admissions.

---

## ☁️ Deployment (Frontend + Backend Split)

This project is now prepared for a split deployment model:

1. **Frontend (Static UI)**
   - Deploy `index.html` to **GitHub Pages** or **Vercel**.
   - The frontend reads `window.HEMS_API_BASE` and calls `${HEMS_API_BASE}/api/...`.
   - For local same-origin usage, keep `HEMS_API_BASE` empty.

2. **Backend (Java API Server)**
   - Deploy Java backend (`WebServer.java` + service/DAO classes + `ojdbc6.jar`) to a Java-capable host such as **Render**, **Railway**, **Fly.io**, or **AWS**.
   - Configure environment variables:
     - `HEMS_DB_USER`
     - `HEMS_DB_PASSWORD`
     - `HEMS_DB_URL` (if not using default)
     - `CORS_ALLOWED_ORIGINS` with your frontend domain(s)

3. **Frontend API Base Configuration**
   - Add this script before the main `<script>` in `index.html` (or inject through your host):
```html
<script>
  window.HEMS_API_BASE = "https://your-backend-host.example.com";
</script>
```

4. **Vercel Frontend**
   - `vercel.json` is included to serve the static SPA entrypoint.
   - Deploy repository root on Vercel for frontend-only hosting.

5. **GitHub Pages Frontend**
   - Publish `index.html` from your selected branch/folder.
   - Ensure `window.HEMS_API_BASE` points to the deployed backend URL.

> ⚠️ GitHub Pages cannot run the Java backend or Oracle JDBC layer; it can host only the static frontend.

---

## 📦 Git Version Control & Repository

- **Repository**: [Hospital-Emergency-Management-System-HEMS-](https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)
- **Author**: Arpit Panigrahi

To push future updates:
```powershell
git add .
git commit -m "Your descriptive commit message"
git push
```
