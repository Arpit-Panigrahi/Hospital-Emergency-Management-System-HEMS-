# Hospital Emergency Management System (HEMS)

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)
[![Vercel Deployment](https://img.shields.io/badge/Vercel-Full--Stack_Hosted-black?style=flat&logo=vercel)](https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)
[![Node.js Backend](https://img.shields.io/badge/Backend-Serverless_API-green)](https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)
[![Java 17+](https://img.shields.io/badge/Java-17+-orange?logo=openjdk)](https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)
[![Database](https://img.shields.io/badge/Database-Oracle%2011g%20%7C%20PostgreSQL-red)](https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)

A robust, multi-tier emergency triage and hospital bed allocation system featuring:
- **☁️ 100% Free Online Cloud Full-Stack**: Single clickable deployment on Vercel running a serverless backend (`/api`) with ACID transaction handling, concurrency checks, and optional PostgreSQL cloud persistence.
- **🏢 University Lab Offline Mode**: Java HTTP REST server and interactive CLI connecting directly to Oracle Database 11g Enterprise via JDBC Type 4 (`ojdbc6.jar`).

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
HEMS/
├── api/
│   └── index.js            # Cloud Serverless Backend API (Vercel Node.js runtime)
├── index.html              # Responsive single-page application (HTML5/CSS3/JS)
├── vercel.json             # Vercel deployment & serverless routing config
├── package.json            # Node.js project descriptor & dependencies
│
├── DBConnection.java       # Centralized JDBC connection factory (Oracle 11g)
├── PatientDAO.java         # Data Access Object (CRUD, queries, JSON serialization)
├── EmergencyService.java   # Business logic & atomic ACID transaction management
├── MainApp.java            # Interactive command-line interface (CLI)
├── WebServer.java          # Built-in lightweight HTTP REST server (Port 8080)
├── schema.sql              # Oracle DDL & seed data script
├── ojdbc6.jar              # Oracle JDBC Type 4 Driver
├── .gitignore              # Ignores compiled bytecode (*.class) and node_modules
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

---

## 🚀 Step-by-Step Execution Guide

### Option 1: ☁️ 1-Click Free Cloud Deployment on Vercel (Online Full-Stack)

Deploy the entire frontend and backend online to Vercel with a single click:

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)

1. Click the **Deploy with Vercel** button above.
2. Sign in with GitHub on [Vercel](https://vercel.com).
3. Vercel automatically forks and deploys both the frontend dashboard and the serverless backend (`/api`) in under 60 seconds.
4. **Instant Access**: Open your assigned `https://<your-project>.vercel.app` URL.
5. **(Optional) Cloud Database Connection**:
   - Go to Vercel Project Settings ➔ **Environment Variables**.
   - Add `DATABASE_URL` with your free connection string from [Neon](https://neon.tech) or [Supabase](https://supabase.com).
   - If omitted, the online server automatically operates with high-resilience in-cloud state pre-seeded with all accounts and beds.

---

### Option 2: 🏢 Running in University Lab (Java & Oracle 11g)

#### Compilation (If Java source is modified)
Open PowerShell or Command Prompt in the project folder:
```powershell
javac -cp ojdbc6.jar *.java
```

---

#### Lab Web Dashboard Server (Localhost:8080)

1. Start the Java HTTP REST Server:
```powershell
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

## 📦 Git Version Control & Repository

- **Repository**: [Hospital-Emergency-Management-System-HEMS-](https://github.com/Arpit-Panigrahi/Hospital-Emergency-Management-System-HEMS-)
- **Author**: Arpit Panigrahi

To push future updates:
```powershell
git add .
git commit -m "Your descriptive commit message"
git push
```
